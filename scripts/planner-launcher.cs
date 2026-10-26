using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

// This launcher only supplies CLI isolation switches absent from the official
// TypeScript SDK. Auth and inference remain inside the pinned official binary.
internal static class PlannerLauncher {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr CreateJobObject(IntPtr attributes, string name);
    [DllImport("kernel32.dll")]
    static extern bool SetInformationJobObject(IntPtr job, int kind, IntPtr info, uint length);
    [DllImport("kernel32.dll")]
    static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);
    [StructLayout(LayoutKind.Sequential)]
    struct BasicLimits {
        public long ProcessTime, JobTime;
        public uint Flags;
        public UIntPtr Minimum, Maximum;
        public uint ActiveProcesses;
        public UIntPtr Affinity;
        public uint Priority, Scheduling;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct IoCounters { public ulong A, B, C, D, E, F; }
    [StructLayout(LayoutKind.Sequential)]
    struct ExtendedLimits {
        public BasicLimits Basic;
        public IoCounters Io;
        public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
    }
    // Windows command-line encoding: quote every argument, doubling backslashes
    // before quotes and the closing quote. Never use cmd.exe or powershell.exe.
    static string Quote(string value) {
        var output = new StringBuilder("\"");
        int slashes = 0;
        foreach (char ch in value) {
            if (ch == '\\') { slashes++; continue; }
            if (ch == '"') { output.Append('\\', slashes * 2 + 1); output.Append(ch); }
            else { output.Append('\\', slashes); output.Append(ch); }
            slashes = 0;
        }
        output.Append('\\', slashes * 2); output.Append('"');
        return output.ToString();
    }
    static int Main(string[] args) {
        if (args.Length == 0 || args[0] != "exec") return 64;
        string binary = Environment.GetEnvironmentVariable("MCE_CODEX_BINARY");
        if (String.IsNullOrEmpty(binary) || !System.IO.Path.IsPathRooted(binary)) return 64;
        IntPtr job = CreateJobObject(IntPtr.Zero, null);
        if (job == IntPtr.Zero) return 70;
        var limits = new ExtendedLimits();
        limits.Basic.Flags = 0x2000; // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        int length = Marshal.SizeOf(typeof(ExtendedLimits));
        IntPtr info = Marshal.AllocHGlobal(length);
        try {
            Marshal.StructureToPtr(limits, info, false);
            if (!SetInformationJobObject(job, 9, info, (uint)length)) return 70;
            var command = new StringBuilder("exec --ignore-user-config --ignore-rules --ephemeral");
            for (int index = 1; index < args.Length; index++) command.Append(" ").Append(Quote(args[index]));
            var start = new ProcessStartInfo(binary, command.ToString());
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            using (var child = Process.Start(start)) {
                if (child == null) return 70;
                if (!AssignProcessToJobObject(job, child.Handle)) { child.Kill(); return 70; }
                child.WaitForExit();
                return child.ExitCode;
            }
        } finally { Marshal.FreeHGlobal(info); CloseHandle(job); }
    }
}
