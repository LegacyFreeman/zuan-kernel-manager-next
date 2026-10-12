package com.zuan.kernelmanager.next;

/**
 * Narrow privileged IPC surface for ZKM's kernel runtime.
 * Kernel paths are read/write addressed; arbitrary command execution stays
 * outside the daemon surface.
 */
interface IZkmRootServiceV3 {
    /** Bumped to force Gradle/AIDL regeneration after IPC API changes. */
    const int API_VERSION = 3;
    boolean ping();
    boolean crashForDiagnostics();
    String readNode(String path);
    String[] readNodes(in String[] paths);
    boolean writeNode(String path, String value);
    boolean[] writeNodes(in String[] paths, in String[] values);
    boolean nodeExists(String path);
    boolean[] nodeExistsBatch(in String[] paths);
    boolean nodeWritable(String path);
    boolean[] nodeWritableBatch(in String[] paths);
    String[] listDirectories(String path);

    /** Debug-only exact-prefix file sandbox used by engine transaction tests. */
    boolean diagnosticWriteFile(String path, String value);
    String diagnosticReadFile(String path);
    boolean diagnosticDeleteFile(String path);
}
