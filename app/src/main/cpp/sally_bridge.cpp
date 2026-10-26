#include <jni.h>
#include <cerrno>
#include <dirent.h>
#include <dlfcn.h>
#include <limits.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <utility>

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
    return env->NewStringUTF("Sally Native Input Bridge 1.0");
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
