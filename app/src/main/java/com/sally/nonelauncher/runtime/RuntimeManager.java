package com.sally.nonelauncher.runtime;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local runtime archive catalog. Import validates structure; it does not execute the runtime. */
public final class RuntimeManager {
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 30000;
    private final Context context;

    public RuntimeManager(Context context) { this.context = context.getApplicationContext(); }

    public File getRuntimeDirectory() {
        File root = new File(context.getFilesDir(), "runtimes");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Cannot create runtime directory");
        return root;
    }

    public List<RuntimeInfo> scan() {
        File[] children = getRuntimeDirectory().listFiles();
        List<RuntimeInfo> result = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                if (!child.isDirectory()) continue;
                RuntimeInfo info = inspect(child);
                if (info != null) result.add(info);
            }
        }
        Collections.sort(result, Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    public RuntimeInfo importZip(Uri uri, String displayName) throws IOException {
        File root = getRuntimeDirectory();
        String base = safeName(displayName);
        File archive = new File(context.getCacheDir(), "runtime-import-" + System.nanoTime() + ".zip");
        File staging = new File(root, ".import-" + System.nanoTime());
        File destination = new File(root, base);
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(archive)) {
                if (in == null) throw new IOException("Cannot open selected file");
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (!staging.mkdirs()) throw new IOException("Cannot create import staging directory");
            unzipSafely(archive, staging);
            File runtimeRoot = findRuntimeRoot(staging, 0);
            if (runtimeRoot == null) throw new IOException("Archive is not a supported Java runtime: expected release and bin/java");
            File actual = runtimeRoot;
            if (!runtimeRoot.equals(staging)) {
                File normalized = new File(staging, "runtime-root");
                if (!runtimeRoot.renameTo(normalized)) throw new IOException("Cannot normalize runtime folder");
                actual = normalized;
            }
            if (destination.exists()) destination = new File(root, base + "-" + (System.currentTimeMillis() / 1000));
            if (!actual.renameTo(destination)) throw new IOException("Cannot save imported runtime");
            RuntimeInfo info = inspect(destination);
            if (info == null) {
                deleteRecursively(destination);
                throw new IOException("Runtime validation failed after import");
            }
            return info;
        } finally {
            deleteRecursively(staging);
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
        }
    }

    private void unzipSafely(File archive, File destination) throws IOException {
        long expanded = 0; int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Archive contains too many files");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe path in archive");
                File out = new File(destination, name);
                String rootPath = destination.getCanonicalPath() + File.separator;
                String outPath = out.getCanonicalPath();
                if (!outPath.equals(destination.getCanonicalPath()) && !outPath.startsWith(rootPath)) throw new IOException("Unsafe path in archive");
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot create archive directory");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create archive directory");
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            expanded += n;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Runtime archive exceeds 1 GiB expanded limit");
                            stream.write(buffer, 0, n);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private RuntimeInfo inspect(File folder) {
        File release = new File(folder, "release");
        File java = new File(folder, "bin/java");
        if (!release.isFile() || !java.isFile()) return null;
        String version = "Unknown";
        String arch = "Unknown";
        try {
            byte[] bytes = new byte[(int) Math.min(release.length(), 65536)];
            try (FileInputStream in = new FileInputStream(release)) {
                int offset = 0, n;
                while (offset < bytes.length && (n = in.read(bytes, offset, bytes.length - offset)) > 0) offset += n;
                String content = new String(bytes, 0, offset, StandardCharsets.UTF_8);
                for (String line : content.split("\\r?\\n")) {
                    if (line.startsWith("JAVA_VERSION=\"")) version = unquote(line.substring(13));
                    else if (line.startsWith("OS_ARCH=\"")) arch = unquote(line.substring(8));
                }
            }
        } catch (IOException ignored) { return null; }
        return new RuntimeInfo(folder.getName(), version, arch, folder, java);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
        return value;
    }

    private File findRuntimeRoot(File folder, int depth) {
        if (depth > 3) return null;
        if (new File(folder, "release").isFile() && new File(folder, "bin/java").isFile()) return folder;
        File[] children = folder.listFiles();
        if (children != null) for (File child : children) if (child.isDirectory()) {
            File found = findRuntimeRoot(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static String safeName(String name) {
        if (name == null) name = "java-runtime";
        name = name.replaceAll("(?i)\\.zip$", "").replaceAll("[^A-Za-z0-9._-]", "-");
        name = name.replaceAll("^[.-]+|[.-]+$", "");
        if (name.isEmpty()) name = "java-runtime";
        return name.length() > 48 ? name.substring(0, 48) : name;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static final class RuntimeInfo {
        public final String name, version, architecture;
        public final File directory, javaExecutable;
        RuntimeInfo(String name, String version, String architecture, File directory, File javaExecutable) {
            this.name = name; this.version = version; this.architecture = architecture;
            this.directory = directory; this.javaExecutable = javaExecutable;
        }
    }
}
