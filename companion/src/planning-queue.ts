import { RequestError } from "./types.js";

interface Job<T = unknown> {
  actor: string;
  signal: AbortSignal;
  work: () => Promise<T>;
  resolve: (value: T) => void;
  reject: (error: unknown) => void;
  abort: () => void;
}

/** One shared inference slot. Queueing never authorizes an operation. */
export class PlanningQueue {
  private active = false;
  private lastActor = "";
  private readonly queued: Job[] = [];
  get busy(): boolean {
    return this.active || this.queued.length > 0;
  }

  run<T>(
    actor: string,
    signal: AbortSignal,
    work: () => Promise<T>,
    queue = true,
  ): Promise<T> {
    if (signal.aborted)
      return Promise.reject(new RequestError("Planning cancelled.", 503));
    if (
      (!queue && this.busy) ||
      this.queued.length >= 8 ||
      this.queued.some((job) => job.actor === actor)
    )
      return Promise.reject(
        new RequestError(
          "Planning queue is full for this player. Retry manually after current work completes.",
          409,
        ),
      );
    return new Promise<T>((resolve, reject) => {
      const job: Job = {
        actor,
        signal,
        work,
        resolve: (value) => resolve(value as T),
        reject,
        abort: () => {
          const index = this.queued.indexOf(job);
          if (index >= 0) {
            this.queued.splice(index, 1);
            reject(new RequestError("Queued planning cancelled.", 503));
          }
        },
      };
      signal.addEventListener("abort", job.abort, { once: true });
      this.queued.push(job);
      this.pump();
    });
  }
  private pump(): void {
    if (this.active || this.queued.length === 0) return;
    // Prefer another actor when possible instead of letting one requester
    // monopolize a global shared subscription slot.
    const next = this.queued.findIndex((job) => job.actor !== this.lastActor);
    const job = this.queued.splice(next < 0 ? 0 : next, 1)[0]!;
    this.active = true;
    this.lastActor = job.actor;
    Promise.resolve()
      .then(() => {
        if (job.signal.aborted)
          throw new RequestError("Planning cancelled.", 503);
        return job.work();
      })
      .then(job.resolve, job.reject)
      .finally(() => {
        job.signal.removeEventListener("abort", job.abort);
        this.active = false;
        this.pump();
      });
  }
}
