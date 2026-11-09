package dev.minecraftai.engineer;

/** One bounded multi-tick companion job, stopped by {@link #close()}; never restored from disk. */
interface PhysicalJob extends AutoCloseable {
    /** Advances one server tick and returns true once the job has finished and closed itself. */
    boolean tick();
    @Override void close();
}
