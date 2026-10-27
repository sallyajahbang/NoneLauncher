#include <jni.h>
#include <cerrno>
#include <dirent.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <limits.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <optional>
#include <set>
#include <fstream>
#include <iterator>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace {
constexpr std::size_t kQueueCapacity = 2048;
struct InputEvent {
    jfloat type;
    jfloat code;
    jfloat action;
    jfloat x;
    jfloat y;
    jfloat value;
};

std::array<InputEvent, kQueueCapacity> gQueue{};
std::size_t gHead = 0;
std::size_t gTail = 0;
std::size_t gCount = 0;
std::mutex gQueueMutex;

jboolean enqueue(const InputEvent& event) {
    const std::lock_guard<std::mutex> lock(gQueueMutex);
    if (gCount == kQueueCapacity) {
        return JNI_FALSE; // Never silently overwrite an older key-up event.
    }
    gQueue[gTail] = event;
    gTail = (gTail + 1) % kQueueCapacity;
    ++gCount;
    return JNI_TRUE;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeBridgeVersion(
    JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("Sally Native Input Bridge 1.1 (relative pointer capture)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeSendInput(
    JNIEnv* /* env */, jobject /* thiz */, jint type, jint code, jint action,
    jfloat x, jfloat y, jfloat value) {
    return enqueue(InputEvent{static_cast<jfloat>(type), static_cast<jfloat>(code),
                              static_cast<jfloat>(action), x, y, value});
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativePollEvents(
    JNIEnv* env, jobject /* thiz */, jint maxEvents) {
    const std::size_t boundedMax = static_cast<std::size_t>(std::clamp(maxEvents, 0, 256));
    std::array<jfloat, 256 * 6> batch{};
    std::size_t eventCount = 0;
    {
        const std::lock_guard<std::mutex> lock(gQueueMutex);
        while (eventCount < boundedMax && gCount > 0) {
            const InputEvent& event = gQueue[gHead];
            const std::size_t offset = eventCount * 6;
            batch[offset] = event.type;
            batch[offset + 1] = event.code;
            batch[offset + 2] = event.action;
            batch[offset + 3] = event.x;
            batch[offset + 4] = event.y;
            batch[offset + 5] = event.value;
            gHead = (gHead + 1) % kQueueCapacity;
            --gCount;
            ++eventCount;
        }
    }

    const jsize resultSize = static_cast<jsize>(eventCount * 6);
    jfloatArray result = env->NewFloatArray(resultSize);
    if (result == nullptr || resultSize == 0) return result;
    env->SetFloatArrayRegion(result, 0, resultSize, batch.data());
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sally_launcher_core_engine_NativeEngineBridge_nativeQueuedEventCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    const std::lock_guard<std::mutex> lock(gQueueMutex);
    return static_cast<jint>(gCount);
}

namespace {
std::mutex gJliProbeMutex;
void* gJliHandle = nullptr;
void* gJvmHandle = nullptr;
std::string gJliAttemptedHome;

bool canonicalizePath(const std::string& path, std::string& output) {
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) return false;
    output.assign(resolved);
    return true;
}

bool pathIsInside(const std::string& root, const std::string& path) {
    return path.size() > root.size() &&
           path.compare(0, root.size(), root) == 0 &&
           path[root.size()] == '/';
}

struct DirectoryIdentity {
    std::uint64_t device;
    std::uint64_t inode;
    bool operator<(const DirectoryIdentity& other) const {
        if (device != other.device) return device < other.device;
        return inode < other.inode;
    }
};

bool findRuntimeFileRecursive(
    const std::string& root,
    const std::string& directory,
    const std::string& wantedName,
    std::string& foundPath,
    unsigned int depth,
    std::size_t& visitedEntries,
    std::set<DirectoryIdentity>& visitedDirectories) {
    if (depth > 12 || visitedEntries > 50000) return false;

    struct stat directoryStat {};
    if (stat(directory.c_str(), &directoryStat) != 0 || !S_ISDIR(directoryStat.st_mode)) return false;
    // Android's stat typedefs differ across ABIs (notably armeabi-v7a).
    // Normalize identity fields to a fixed-width unsigned type to avoid
    // list-initialization narrowing errors on 32-bit NDK targets.
    const DirectoryIdentity identity{
        static_cast<std::uint64_t>(directoryStat.st_dev),
        static_cast<std::uint64_t>(directoryStat.st_ino)};
    if (!visitedDirectories.insert(identity).second) return false;

    DIR* handle = opendir(directory.c_str());
    if (handle == nullptr) return false;
    bool found = false;
    while (!found) {
        errno = 0;
        dirent* entry = readdir(handle);
        if (entry == nullptr) break;
        const std::string name(entry->d_name);
        if (name == "." || name == "..") continue;
        ++visitedEntries;
        if (visitedEntries > 50000) break;

        const std::string candidate = directory + "/" + name;
        struct stat itemStat {};
        if (stat(candidate.c_str(), &itemStat) != 0) continue;
        std::string canonicalCandidate;
        if (!canonicalizePath(candidate, canonicalCandidate) ||
            !pathIsInside(root, canonicalCandidate)) continue;

        if (name == wantedName && S_ISREG(itemStat.st_mode)) {
            foundPath = canonicalCandidate;
            found = true;
            break;
        }
        if (S_ISDIR(itemStat.st_mode)) {
            found = findRuntimeFileRecursive(root, canonicalCandidate, wantedName,
                                             foundPath, depth + 1, visitedEntries,
                                             visitedDirectories);
        }
    }
    closedir(handle);
    return found;
}

bool findRuntimeFile(const std::string& root, const std::string& wantedName, std::string& result) {
    std::size_t entries = 0;
    std::set<DirectoryIdentity> visited;
    return findRuntimeFileRecursive(root, root, wantedName, result, 0, entries, visited);
}

bool regularFileExists(const std::string& path) {
    struct stat itemStat {};
    return stat(path.c_str(), &itemStat) == 0 && S_ISREG(itemStat.st_mode);
}

std::string currentDlError() {
    const char* error = dlerror();
    return error == nullptr ? std::string("no linker detail available") : std::string(error);
}

jstring returnProbeResult(JNIEnv* env, const std::string& prefix, const std::string& details) {
    return env->NewStringUTF((prefix + "|" + details).c_str());
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sally_launcher_core_engine_JliBridgeDiagnostics_nativeProbeJli(
    JNIEnv* env, jobject /* thiz */, jstring runtimeHomeString) {
    if (runtimeHomeString == nullptr) {
        return returnProbeResult(env, "ERROR", "Runtime home path is null.");
    }

    const char* runtimeHomeChars = env->GetStringUTFChars(runtimeHomeString, nullptr);
    if (runtimeHomeChars == nullptr) {
        return returnProbeResult(env, "ERROR", "Could not read runtime home path from Java.");
    }
    const std::string requestedHome(runtimeHomeChars);
    env->ReleaseStringUTFChars(runtimeHomeString, runtimeHomeChars);

    std::string home;
    if (!canonicalizePath(requestedHome, home)) {
        return returnProbeResult(env, "ERROR", "Runtime home does not resolve to an accessible path.");
    }
    struct stat homeStat {};
    if (stat(home.c_str(), &homeStat) != 0 || !S_ISDIR(homeStat.st_mode)) {
        return returnProbeResult(env, "ERROR", "Runtime home is not a directory.");
    }
    if (!regularFileExists(home + "/release") || !regularFileExists(home + "/bin/java")) {
        return returnProbeResult(env, "ERROR", "Runtime is incomplete: release metadata or bin/java is missing.");
    }

    std::string jliPath;
    std::string jvmPath;
    if (!findRuntimeFile(home, "libjli.so", jliPath)) {
        return returnProbeResult(env, "ERROR", "Could not locate libjli.so inside the installed runtime.");
    }
    if (!findRuntimeFile(home, "libjvm.so", jvmPath)) {
        return returnProbeResult(env, "ERROR", "Could not locate libjvm.so inside the installed runtime.");
    }

    const std::lock_guard<std::mutex> lock(gJliProbeMutex);
    if (!gJliAttemptedHome.empty() && gJliAttemptedHome != home) {
        return returnProbeResult(
            env, "ERROR",
            "A runtime library was already mapped from " + gJliAttemptedHome +
                ". Restart Sally Launcher before probing a different runtime; JVM libraries are process-global.");
    }
    if (gJliAttemptedHome.empty()) gJliAttemptedHome = home;

    std::ostringstream details;
    details << "Runtime home: " << home << "\n"
            << "libjli.so: " << jliPath << "\n"
            << "libjvm.so: " << jvmPath << "\n";

    if (gJliHandle == nullptr) {
        dlerror(); // Clear any stale error from an earlier dynamic-load attempt.
        gJliHandle = dlopen(jliPath.c_str(), RTLD_LAZY | RTLD_GLOBAL);
        if (gJliHandle == nullptr) {
            const std::string firstError = currentDlError();
            details << "Initial libjli.so load failed: " << firstError << "\n";
            dlerror();
            if (gJvmHandle == nullptr) {
                gJvmHandle = dlopen(jvmPath.c_str(), RTLD_LAZY | RTLD_GLOBAL);
            }
            const std::string jvmError = gJvmHandle == nullptr ? currentDlError() : std::string();
            dlerror();
            gJliHandle = dlopen(jliPath.c_str(), RTLD_LAZY | RTLD_GLOBAL);
            if (gJliHandle == nullptr) {
                details << "Retry after loading libjvm.so failed: " << currentDlError() << "\n";
                if (gJvmHandle == nullptr) details << "libjvm.so load detail: " << jvmError << "\n";
                return returnProbeResult(env, "ERROR", details.str());
            }
        }
    }

    if (gJvmHandle == nullptr) {
        dlerror();
        gJvmHandle = dlopen(jvmPath.c_str(), RTLD_LAZY | RTLD_GLOBAL);
        if (gJvmHandle == nullptr) {
            details << "libjvm.so load failed: " << currentDlError() << "\n";
            return returnProbeResult(env, "ERROR", details.str());
        }
    }

    dlerror();
    void* launchSymbol = dlsym(gJliHandle, "JLI_Launch");
    if (launchSymbol == nullptr) {
        details << "libjli.so loaded, but JLI_Launch is not exported: " << currentDlError() << "\n";
        return returnProbeResult(env, "ERROR", details.str());
    }

    details << "libjli.so loaded successfully.\n"
            << "libjvm.so loaded successfully.\n"
            << "JLI_Launch symbol resolved.\n"
            << "No JVM was started and no game arguments were executed.";
    return returnProbeResult(env, "OK", details.str());
}


namespace {
using JliLaunchFunction = jint (*)(
    int, char**, int, const char**, int, const char**, const char*, const char*,
    const char*, const char*, jboolean, jboolean, jboolean, jint);

bool gJliVersionAttempted = false;

void collectRuntimeLibraryDirectories(
    const std::string& root, const std::string& directory, unsigned int depth,
    std::size_t& visited, std::set<std::string>& found) {
    if (depth > 12 || visited > 50000) return;
    std::string canonical;
    if (!canonicalizePath(directory, canonical) ||
        (canonical != root && !pathIsInside(root, canonical))) return;
    DIR* handle = opendir(canonical.c_str());
    if (handle == nullptr) return;
    bool hasSharedObject = false;
    std::vector<std::string> subdirectories;
    while (true) {
        errno = 0;
        dirent* entry = readdir(handle);
        if (entry == nullptr) break;
        const std::string name(entry->d_name);
        if (name == "." || name == "..") continue;
        ++visited;
        if (visited > 50000) break;
        const std::string candidate = canonical + "/" + name;
        struct stat itemStat {};
        if (stat(candidate.c_str(), &itemStat) != 0) continue;
        std::string canonicalCandidate;
        if (!canonicalizePath(candidate, canonicalCandidate) ||
            (canonicalCandidate != root && !pathIsInside(root, canonicalCandidate))) continue;
        if (S_ISREG(itemStat.st_mode) && name.size() > 3 && name.substr(name.size() - 3) == ".so") {
            hasSharedObject = true;
        } else if (S_ISDIR(itemStat.st_mode)) {
            subdirectories.push_back(canonicalCandidate);
        }
    }
    closedir(handle);
    if (hasSharedObject) found.insert(canonical);
    for (const auto& subdirectory : subdirectories) {
        if (visited > 50000) break;
        collectRuntimeLibraryDirectories(root, subdirectory, depth + 1, visited, found);
    }
}

void addPathOnce(std::vector<std::string>& paths, const std::string& path) {
    if (path.empty()) return;
    if (std::find(paths.begin(), paths.end(), path) == paths.end()) paths.push_back(path);
}

void updateAndroidLoaderPath(const std::string& libraryPath) {
    using UpdatePathFunction = void (*)(char*);
    void* dlHandle = dlopen("libdl.so", RTLD_NOW | RTLD_GLOBAL);
    UpdatePathFunction update = reinterpret_cast<UpdatePathFunction>(dlsym(RTLD_DEFAULT, "android_update_LD_LIBRARY_PATH"));
    if (update == nullptr) update = reinterpret_cast<UpdatePathFunction>(dlsym(RTLD_DEFAULT, "__loader_android_update_LD_LIBRARY_PATH"));
    if (update == nullptr && dlHandle != nullptr) {
        update = reinterpret_cast<UpdatePathFunction>(dlsym(dlHandle, "android_update_LD_LIBRARY_PATH"));
        if (update == nullptr) update = reinterpret_cast<UpdatePathFunction>(dlsym(dlHandle, "__loader_android_update_LD_LIBRARY_PATH"));
    }
    if (update != nullptr) update(const_cast<char*>(libraryPath.c_str()));
    if (dlHandle != nullptr) dlclose(dlHandle);
}

class ScopedEnvironment {
public:
    ScopedEnvironment(const std::string& home, const std::string& libraryPath, const std::string& path)
        : oldJavaHome(read("JAVA_HOME")), oldLdLibraryPath(read("LD_LIBRARY_PATH")), oldPath(read("PATH")) {
        setenv("JAVA_HOME", home.c_str(), 1);
        setenv("LD_LIBRARY_PATH", libraryPath.c_str(), 1);
        setenv("PATH", path.c_str(), 1);
        updateAndroidLoaderPath(libraryPath);
    }

    ~ScopedEnvironment() {
        restore("JAVA_HOME", oldJavaHome);
        restore("LD_LIBRARY_PATH", oldLdLibraryPath);
        restore("PATH", oldPath);
        updateAndroidLoaderPath(oldLdLibraryPath.has_value() ? oldLdLibraryPath.value() : std::string());
    }

    ScopedEnvironment(const ScopedEnvironment&) = delete;
    ScopedEnvironment& operator=(const ScopedEnvironment&) = delete;

private:
    using SavedValue = std::optional<std::string>;
    static SavedValue read(const char* name) {
        const char* value = std::getenv(name);
        if (value == nullptr) return std::nullopt;
        return std::string(value);
    }
    static void restore(const char* name, const SavedValue& value) {
        if (value.has_value()) setenv(name, value->c_str(), 1);
        else unsetenv(name);
    }

    SavedValue oldJavaHome;
    SavedValue oldLdLibraryPath;
    SavedValue oldPath;
};

std::string readSmallTextFile(const std::string& path, std::size_t limit) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return {};
    std::string value;
    value.reserve(std::min<std::size_t>(limit, 8192));
    char buffer[4096];
    while (input && value.size() < limit) {
        const auto remaining = limit - value.size();
        input.read(buffer, static_cast<std::streamsize>(std::min<std::size_t>(sizeof(buffer), remaining)));
        const auto count = input.gcount();
        if (count <= 0) break;
        value.append(buffer, static_cast<std::size_t>(count));
    }
    if (value.size() == limit) value += "\n[log truncated]";
    return value;
}

std::string getReleaseValue(const std::string& home, const std::string& key, const std::string& fallback) {
    std::ifstream input(home + "/release");
    if (!input) return fallback;
    const std::string prefix = key + "=\"";
    std::string line;
    while (std::getline(input, line)) {
        if (line.rfind(prefix, 0) == 0 && line.size() > prefix.size() && line.back() == '"') {
            return line.substr(prefix.size(), line.size() - prefix.size() - 1);
        }
    }
    return fallback;
}

jstring makeSmokeResponse(JNIEnv* env, const std::string& prefix, const std::string& details) {
    return env->NewStringUTF((prefix + "|" + details).c_str());
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sally_launcher_core_engine_JliRuntimeSmokeTest_nativeRunVersion(
    JNIEnv* env, jobject /* thiz */, jstring runtimeHomeString,
    jstring workingDirectoryString, jstring logFileString) {
    if (runtimeHomeString == nullptr || workingDirectoryString == nullptr || logFileString == nullptr) {
        return makeSmokeResponse(env, "ERROR", "Runtime, working directory, and log path are required.");
    }
    const char* homeChars = env->GetStringUTFChars(runtimeHomeString, nullptr);
    const char* workChars = env->GetStringUTFChars(workingDirectoryString, nullptr);
    const char* logChars = env->GetStringUTFChars(logFileString, nullptr);
    if (homeChars == nullptr || workChars == nullptr || logChars == nullptr) {
        if (homeChars) env->ReleaseStringUTFChars(runtimeHomeString, homeChars);
        if (workChars) env->ReleaseStringUTFChars(workingDirectoryString, workChars);
        if (logChars) env->ReleaseStringUTFChars(logFileString, logChars);
        return makeSmokeResponse(env, "ERROR", "Could not read the native runtime test paths.");
    }
    const std::string requestedHome(homeChars);
    const std::string requestedWork(workChars);
    const std::string requestedLog(logChars);
    env->ReleaseStringUTFChars(runtimeHomeString, homeChars);
    env->ReleaseStringUTFChars(workingDirectoryString, workChars);
    env->ReleaseStringUTFChars(logFileString, logChars);

    std::string home, work, logPath;
    if (!canonicalizePath(requestedHome, home) || !canonicalizePath(requestedWork, work)) {
        return makeSmokeResponse(env, "ERROR", "Runtime home or working directory does not resolve.");
    }
    const std::string logParent = requestedLog.substr(0, requestedLog.find_last_of('/'));
    std::string canonicalLogParent;
    if (logParent.empty() || !canonicalizePath(logParent, canonicalLogParent)) {
        return makeSmokeResponse(env, "ERROR", "Log directory does not exist or cannot be resolved.");
    }
    logPath = canonicalLogParent + "/" + requestedLog.substr(requestedLog.find_last_of('/') + 1);
    struct stat st {};
    if (stat(home.c_str(), &st) != 0 || !S_ISDIR(st.st_mode) ||
        !regularFileExists(home + "/release") || !regularFileExists(home + "/bin/java")) {
        return makeSmokeResponse(env, "ERROR", "Runtime is incomplete: release or bin/java is missing.");
    }
    if (stat(work.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
        return makeSmokeResponse(env, "ERROR", "Working directory is not an accessible directory.");
    }

    const std::lock_guard<std::mutex> lock(gJliProbeMutex);
    if (gJliVersionAttempted) {
        return makeSmokeResponse(env, "ERROR", "A JVM version check has already run in this process. Restart Sally before retrying; embedded JVMs are process-global.");
    }
    if (!gJliAttemptedHome.empty() && gJliAttemptedHome != home) {
        return makeSmokeResponse(env, "ERROR", "Another runtime is already mapped in this process. Restart Sally before selecting a different Java runtime.");
    }
    std::string jliPath, jvmPath;
    if (!findRuntimeFile(home, "libjli.so", jliPath)) {
        return makeSmokeResponse(env, "ERROR", "libjli.so was not found inside the selected runtime.");
    }
    if (!findRuntimeFile(home, "libjvm.so", jvmPath)) {
        return makeSmokeResponse(env, "ERROR", "libjvm.so was not found inside the selected runtime.");
    }

    std::set<std::string> directorySet;
    std::size_t visited = 0;
    collectRuntimeLibraryDirectories(home, home, 0, visited, directorySet);
    std::vector<std::string> libraryDirs(directorySet.begin(), directorySet.end());
    addPathOnce(libraryDirs, jliPath.substr(0, jliPath.find_last_of('/')));
    addPathOnce(libraryDirs, jvmPath.substr(0, jvmPath.find_last_of('/')));
    const char* existingLdPath = std::getenv("LD_LIBRARY_PATH");
    if (existingLdPath != nullptr) {
        std::string existing(existingLdPath);
        std::size_t start = 0;
        while (start <= existing.size()) {
            const std::size_t end = existing.find(':', start);
            addPathOnce(libraryDirs, existing.substr(start, end == std::string::npos ? std::string::npos : end - start));
            if (end == std::string::npos) break;
            start = end + 1;
        }
    }
    std::string ldLibraryPath;
    for (const auto& item : libraryDirs) {
        if (!ldLibraryPath.empty()) ldLibraryPath += ':';
        ldLibraryPath += item;
    }
    const std::string pathValue = home + "/bin:" + (std::getenv("PATH") ? std::getenv("PATH") : "");
    // Environment and Android linker search-path changes are process-global. Restore them on
    // every return path so the diagnostic cannot leak the selected JRE into other app code.
    ScopedEnvironment scopedEnvironment(home, ldLibraryPath, pathValue);

    dlerror();
    if (gJliHandle == nullptr) gJliHandle = dlopen(jliPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (gJliHandle == nullptr) {
        return makeSmokeResponse(env, "ERROR", std::string("Could not load libjli.so: ") + currentDlError());
    }
    dlerror();
    if (gJvmHandle == nullptr) gJvmHandle = dlopen(jvmPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (gJvmHandle == nullptr) {
        return makeSmokeResponse(env, "ERROR", std::string("Could not load libjvm.so: ") + currentDlError());
    }
    dlerror();
    auto launch = reinterpret_cast<JliLaunchFunction>(dlsym(gJliHandle, "JLI_Launch"));
    if (launch == nullptr) {
        return makeSmokeResponse(env, "ERROR", std::string("JLI_Launch symbol lookup failed: ") + currentDlError());
    }

    // The runtime libraries have been mapped; do not allow a different JRE in this
    // process even if a later filesystem check prevents the smoke test from starting.
    gJliAttemptedHome = home;
    // Android app processes usually start with cwd "/", and SELinux denies open("/") for apps,
    // so remember the old cwd as a path (getcwd) instead of an fd. Fall back to "/".
    char oldCwdBuffer[4096];
    const std::string oldCwd = getcwd(oldCwdBuffer, sizeof(oldCwdBuffer)) != nullptr
        ? std::string(oldCwdBuffer) : std::string("/");
    if (chdir(work.c_str()) != 0) {
        return makeSmokeResponse(env, "ERROR",
            std::string("Could not switch to the JVM test working directory: ") + work + " (" + std::strerror(errno) + ")");
    }
    const int logFd = open(logPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (logFd < 0) {
        (void) chdir(oldCwd.c_str());
        return makeSmokeResponse(env, "ERROR", "Could not open the JVM test log file.");
    }
    fflush(nullptr);
    const int oldStdout = dup(STDOUT_FILENO);
    const int oldStderr = dup(STDERR_FILENO);
    bool stdoutRedirected = dup2(logFd, STDOUT_FILENO) >= 0;
    bool stderrRedirected = dup2(logFd, STDERR_FILENO) >= 0;
    if (!stdoutRedirected || !stderrRedirected) {
        if (oldStdout >= 0) { dup2(oldStdout, STDOUT_FILENO); close(oldStdout); }
        if (oldStderr >= 0) { dup2(oldStderr, STDERR_FILENO); close(oldStderr); }
        close(logFd);
        (void) chdir(oldCwd.c_str());
        return makeSmokeResponse(env, "ERROR", "Could not redirect JVM diagnostic output.");
    }

    // Android-safe HotSpot flags: JDK 17's cgroup/container probing and /tmp perf-data mmap are
    // common crash/hang sources on Android; CDS and G1 add risk to a one-shot -version check.
    std::vector<std::string> argStorage = {
        "java",
        "-XX:-UseContainerSupport",
        "-XX:-UsePerfData",
        "-XX:+UseSerialGC",
        "-Xshare:off",
        "-XX:ErrorFile=" + work + "/hs_err_pid%p.log",
        "-Djava.io.tmpdir=" + work,
        "-version",
    };
    std::vector<char*> argv;
    for (auto& argument : argStorage) argv.push_back(&argument[0]);
    argv.push_back(nullptr);
    const std::string fullVersion = getReleaseValue(home, "JAVA_VERSION", "unknown");
    const std::string dotVersion = fullVersion.rfind("1.", 0) == 0
        ? fullVersion.substr(0, std::min<std::size_t>(3, fullVersion.size()))
        : fullVersion.substr(0, fullVersion.find('.'));
    // JLI invocation is one-shot for this process. Even a `-version` JVM may leave
    // process-global VM state mapped after returning.
    gJliVersionAttempted = true;
    const jint exitCode = launch(
        static_cast<int>(argStorage.size()), argv.data(), 0, nullptr, 0, nullptr,
        fullVersion.c_str(), dotVersion.c_str(), "java", "SallyLauncher",
        JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);

    fflush(nullptr);
    dup2(oldStdout, STDOUT_FILENO);
    dup2(oldStderr, STDERR_FILENO);
    close(oldStdout);
    close(oldStderr);
    close(logFd);
    (void) chdir(oldCwd.c_str());

    const std::string output = readSmallTextFile(logPath, 48 * 1024);
    std::ostringstream response;
    response << "JLI_Launch returned " << exitCode << ". Log: " << logPath;
    if (!output.empty()) response << "\n\n" << output;
    return makeSmokeResponse(env, exitCode == 0 ? "OK" : "ERROR", response.str());
}
