package com.dsh.dakacheck;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/**
 * 用系统 JobScheduler 保活：一次性任务，执行完立刻排下一个。
 * setPeriodic 最小 15 分钟，所以用这种方式才能跑到更短的间隔。
 */
public class KeepAliveJob extends JobService {
    public static final int JOB_ID = 4101;

    /** 间隔：15 分钟（系统对更短的间隔会压制）。 */
    public static final long INTERVAL_MS = 15 * 60_000L;

    @Override
    public boolean onStartJob(JobParameters params) {
        try {
            Log.i("Daka", "job tick");
            Timeline.add(this, "系统保活任务执行");
            Guard.ensure(this);
            Widgets.refreshAll(this);
            Archive.syncAsync(this);
        } catch (Throwable t) {
            Log.i("Daka", "job tick failed: " + t);
        }
        // 先把下一个排上，再结束当前任务
        Scheduler.ensureKeepAlive(this);
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Scheduler.ensureKeepAlive(this);
        return true;
    }
}
