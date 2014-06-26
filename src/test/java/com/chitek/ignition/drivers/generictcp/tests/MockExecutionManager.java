package com.chitek.ignition.drivers.generictcp.tests;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.chitek.TestUtils.MockExecutor;
import com.inductiveautomation.ignition.common.execution.ExecutionManager;
import com.inductiveautomation.ignition.common.execution.SelfSchedulingRunnable;

public class MockExecutionManager extends MockExecutor implements ExecutionManager {

	@Override
	public void register(String paramString1, String paramString2, Runnable paramRunnable, int paramInt) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void register(String paramString1, String paramString2, Runnable paramRunnable, int paramInt, TimeUnit paramTimeUnit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void register(String paramString1, String paramString2, SelfSchedulingRunnable paramSelfSchedulingRunnable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerWithInitialDelay(String paramString1, String paramString2, Runnable paramRunnable, int paramInt1, int paramInt2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerWithInitialDelay(String paramString1, String paramString2, Runnable paramRunnable, int paramInt1, TimeUnit paramTimeUnit, int paramInt2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerAtFixedRate(String paramString1, String paramString2, Runnable paramRunnable, int paramInt, TimeUnit paramTimeUnit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerAtFixedRateWithInitialDelay(String paramString1, String paramString2, Runnable paramRunnable, int paramInt1, TimeUnit paramTimeUnit, int paramInt2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable paramRunnable, long paramLong1, long paramLong2, TimeUnit paramTimeUnit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unRegister(String paramString1, String paramString2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unRegisterAll(String paramString) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void shutdown() {
		throw new UnsupportedOperationException();
	}

}
