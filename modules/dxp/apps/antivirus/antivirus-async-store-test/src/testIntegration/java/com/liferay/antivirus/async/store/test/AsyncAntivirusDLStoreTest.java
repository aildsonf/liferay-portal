/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Liferay Enterprise
 * Subscription License ("License"). You may not use this file except in
 * compliance with the License. You can obtain a copy of the License by
 * contacting Liferay, Inc. See the License for the specific language governing
 * permissions and limitations under the License, including but not limited to
 * distribution rights of the Software.
 *
 *
 *
 */

package com.liferay.antivirus.async.store.test;

import com.liferay.antivirus.async.store.configuration.AntivirusAsyncConfiguration;
import com.liferay.antivirus.async.store.constants.AntivirusAsyncDestinationNames;
import com.liferay.antivirus.async.store.event.AntivirusAsyncEvent;
import com.liferay.antivirus.async.store.event.AntivirusAsyncEventListener;
import com.liferay.antivirus.async.store.jmx.AntivirusAsyncStatisticsManagerMBean;
import com.liferay.antivirus.async.store.retry.AntivirusAsyncRetryScheduler;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.antivirus.AntivirusScanner;
import com.liferay.document.library.kernel.antivirus.AntivirusScannerException;
import com.liferay.document.library.kernel.antivirus.AntivirusVirusFoundException;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.document.library.test.util.DLTestUtil;
import com.liferay.petra.concurrent.NoticeableThreadPoolExecutor;
import com.liferay.petra.concurrent.ThreadPoolHandlerAdapter;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageBusUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.File;
import java.io.InputStream;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Raymond Augé
 */
@RunWith(Arquillian.class)
public class AsyncAntivirusDLStoreTest {

	@ClassRule
	@Rule
	public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
		new LiferayIntegrationTestRule();

	@BeforeClass
	public static void setUpClass() {
		Bundle bundle = FrameworkUtil.getBundle(
			AsyncAntivirusDLStoreTest.class);

		_bundleContext = bundle.getBundleContext();
	}

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testEventMissing() throws Exception {
		AtomicBoolean missingFired = new AtomicBoolean();
		AtomicBoolean scannerWasCalled = new AtomicBoolean();

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(() -> scannerWasCalled.set(true)),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.MISSING,
						() -> missingFired.set(true)
					).build()),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1, true,
				() -> {
					Message message = new Message();

					message.put("companyId", 0);
					message.put("fileName", "test");
					message.put("repositoryId", 0);
					message.put("versionLabel", "test");

					_messageBus.sendMessage(
						AntivirusAsyncDestinationNames.ANTIVIRUS, message);

					Assert.assertTrue(missingFired.get());
					Assert.assertFalse(scannerWasCalled.get());
				});
		}
		finally {
			eventListenerServiceRegistration.unregister();
			antivirusScannerServiceRegistration.unregister();
		}
	}

	@Test
	public void testEventProcessingError() throws Exception {
		AtomicBoolean prepareEventFired = new AtomicBoolean();
		AtomicBoolean processingErrorEventFired = new AtomicBoolean();
		AtomicBoolean retryScheduled = new AtomicBoolean();

		ServiceRegistration<AntivirusAsyncRetryScheduler>
			schedulerHelperServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncRetryScheduler.class,
				message -> retryScheduled.set(true),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, 100));

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(
						() -> {
							throw new AntivirusScannerException(
								AntivirusScannerException.PROCESS_FAILURE);
						}),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						() -> prepareEventFired.set(true)
					).put(
						AntivirusAsyncEvent.PROCESSING_ERROR,
						() -> processingErrorEventFired.set(true)
					).build()),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, -100));

		try {
			_withAsyncAntivirusConfiguration(
				1, 1, true,
				() -> {
					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					try (LogCapture logCapture =
							LoggerTestUtil.configureLog4JLogger(
								StringBundler.concat(
									"com.liferay.antivirus.async.web.internal.",
									"notifications.",
									"AntivirusAsyncNotification",
									"EventListener"),
								LoggerTestUtil.ERROR)) {

						DLTestUtil.addDLFileEntry(dlFolder.getFolderId());
					}

					Assert.assertTrue(prepareEventFired.get());
					Assert.assertTrue(processingErrorEventFired.get());
					Assert.assertTrue(retryScheduled.get());
				});
		}
		finally {
			antivirusScannerServiceRegistration.unregister();
			eventListenerServiceRegistration.unregister();
			schedulerHelperServiceRegistration.unregister();
		}
	}

	@Test
	public void testEventSizeExceeded() throws Exception {
		AtomicBoolean prepareEventFired = new AtomicBoolean();
		AtomicBoolean scannerWasCalled = new AtomicBoolean();
		AtomicBoolean sizeExceededFired = new AtomicBoolean();

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(
						() -> {
							scannerWasCalled.set(true);

							throw new AntivirusScannerException(
								AntivirusScannerException.SIZE_LIMIT_EXCEEDED);
						}),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						() -> prepareEventFired.set(true)
					).put(
						AntivirusAsyncEvent.SIZE_EXCEEDED,
						() -> sizeExceededFired.set(true)
					).build()),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1, true,
				() -> {
					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					DLTestUtil.addDLFileEntry(dlFolder.getFolderId());

					Assert.assertTrue(prepareEventFired.get());
					Assert.assertTrue(scannerWasCalled.get());
					Assert.assertTrue(sizeExceededFired.get());
				});
		}
		finally {
			eventListenerServiceRegistration.unregister();
			antivirusScannerServiceRegistration.unregister();
		}
	}

	@Test
	public void testEventSuccess() throws Exception {
		AtomicBoolean prepareEventFired = new AtomicBoolean();
		AtomicBoolean successFired = new AtomicBoolean();

		AtomicBoolean scannerWasCalled = new AtomicBoolean();

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(() -> scannerWasCalled.set(true)),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						() -> prepareEventFired.set(true)
					).put(
						AntivirusAsyncEvent.SUCCESS,
						() -> successFired.set(true)
					).build()),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1, true,
				() -> {
					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					DLTestUtil.addDLFileEntry(dlFolder.getFolderId());

					Assert.assertTrue(prepareEventFired.get());
					Assert.assertTrue(scannerWasCalled.get());
					Assert.assertTrue(successFired.get());
				});
		}
		finally {
			eventListenerServiceRegistration.unregister();
			antivirusScannerServiceRegistration.unregister();
		}
	}

	@Test
	public void testEventVirusFound() throws Exception {
		AtomicBoolean prepareEventFired = new AtomicBoolean();
		AtomicBoolean virusFoundFired = new AtomicBoolean();

		AtomicBoolean scannerWasCalled = new AtomicBoolean();

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(
						() -> {
							scannerWasCalled.set(true);

							throw new AntivirusVirusFoundException(
								"Virus detected in stream", "foo.virus");
						}),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						() -> prepareEventFired.set(true)
					).put(
						AntivirusAsyncEvent.VIRUS_FOUND,
						() -> virusFoundFired.set(true)
					).build()),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1, true,
				() -> {
					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					DLTestUtil.addDLFileEntry(dlFolder.getFolderId());

					Assert.assertTrue(prepareEventFired.get());
					Assert.assertTrue(scannerWasCalled.get());
					Assert.assertTrue(virusFoundFired.get());
				});
		}
		finally {
			eventListenerServiceRegistration.unregister();
			antivirusScannerServiceRegistration.unregister();
		}
	}

	@Test
	public void testQueueOverflow() throws Exception {
		int numberOfFilesToProcess = 10;

		AtomicInteger prepareEventFired = new AtomicInteger();

		AtomicInteger retryScheduled = new AtomicInteger();

		ServiceRegistration<AntivirusAsyncRetryScheduler>
			schedulerHelperServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncRetryScheduler.class,
				message -> retryScheduled.incrementAndGet(),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, 100));

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(
						() -> {
							try {
								Thread.sleep(Long.MAX_VALUE);
							}
							catch (InterruptedException interruptedException) {
							}
						}),
					null);

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						prepareEventFired::incrementAndGet
					).put(
						AntivirusAsyncEvent.SUCCESS,
						() -> {
						}
					).build()),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, -100));

		try {
			_withAsyncAntivirusConfiguration(
				1, 10, false,
				() -> {
					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					for (int i = numberOfFilesToProcess; i > 0; i--) {
						DLTestUtil.addDLFileEntry(dlFolder.getFolderId());
					}

					Assert.assertEquals(
						numberOfFilesToProcess, prepareEventFired.get());
					Assert.assertTrue(
						String.valueOf(retryScheduled.get()),
						retryScheduled.get() > 0);
				});
		}
		finally {
			antivirusScannerServiceRegistration.unregister();
			eventListenerServiceRegistration.unregister();
			schedulerHelperServiceRegistration.unregister();
		}
	}

	@Test
	public void testStatistics() throws Exception {
		int numberOfFilesToProcess = 100;

		AtomicInteger prepareEventFired = new AtomicInteger();
		AtomicInteger processingErrorEventFired = new AtomicInteger();
		AtomicInteger sizeExceededEventFired = new AtomicInteger();
		AtomicInteger successEventFired = new AtomicInteger();
		AtomicInteger virusFoundEventFired = new AtomicInteger();

		Random random = new Random();

		ServiceRegistration<AntivirusAsyncRetryScheduler>
			schedulerHelperServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncRetryScheduler.class,
				message -> {
				},
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, 100));

		ServiceRegistration<AntivirusScanner>
			antivirusScannerServiceRegistration =
				_bundleContext.registerService(
					AntivirusScanner.class,
					new MockAntivirusScanner(
						() -> {
							int choice = random.nextInt(4);

							if (choice == 1) {
								throw new AntivirusVirusFoundException(
									"Virus detected in stream", "foo.virus");
							}
							else if (choice == 2) {
								throw new AntivirusScannerException(
									AntivirusScannerException.
										SIZE_LIMIT_EXCEEDED);
							}
							else if (choice == 3) {
								throw new AntivirusScannerException(
									AntivirusScannerException.PROCESS_FAILURE);
							}
						}),
					MapUtil.singletonDictionary(
						Constants.SERVICE_RANKING, 100));

		ServiceRegistration<AntivirusAsyncEventListener>
			eventListenerServiceRegistration = _bundleContext.registerService(
				AntivirusAsyncEventListener.class,
				_create(
					HashMapBuilder.<AntivirusAsyncEvent, Runnable>put(
						AntivirusAsyncEvent.PREPARE,
						prepareEventFired::incrementAndGet
					).put(
						AntivirusAsyncEvent.PROCESSING_ERROR,
						processingErrorEventFired::incrementAndGet
					).put(
						AntivirusAsyncEvent.SIZE_EXCEEDED,
						sizeExceededEventFired::incrementAndGet
					).put(
						AntivirusAsyncEvent.SUCCESS,
						successEventFired::incrementAndGet
					).put(
						AntivirusAsyncEvent.VIRUS_FOUND,
						virusFoundEventFired::incrementAndGet
					).build()),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, -100));

		try {
			_withAsyncAntivirusConfiguration(
				5, 10, true,
				() -> {
					ServiceReference<AntivirusAsyncStatisticsManagerMBean>
						serviceReference = _bundleContext.getServiceReference(
							AntivirusAsyncStatisticsManagerMBean.class);

					AntivirusAsyncStatisticsManagerMBean
						antivirusAsyncStatisticsManagerMBean =
							_bundleContext.getService(serviceReference);

					Assert.assertNotNull(antivirusAsyncStatisticsManagerMBean);

					antivirusAsyncStatisticsManagerMBean.refresh();

					DLFolder dlFolder = DLTestUtil.addDLFolder(
						_group.getGroupId());

					try (LogCapture logCapture =
							LoggerTestUtil.configureLog4JLogger(
								StringBundler.concat(
									"com.liferay.antivirus.async.web.internal.",
									"notifications.",
									"AntivirusAsyncNotificationEventListener"),
								LoggerTestUtil.ERROR)) {

						for (int i = numberOfFilesToProcess; i > 0; i--) {
							DLTestUtil.addDLFileEntry(dlFolder.getFolderId());
						}
					}

					Assert.assertEquals(
						numberOfFilesToProcess, prepareEventFired.get());
					Assert.assertEquals(
						processingErrorEventFired.get(),
						antivirusAsyncStatisticsManagerMBean.
							getProcessingErrorCount());
					Assert.assertEquals(
						sizeExceededEventFired.get(),
						antivirusAsyncStatisticsManagerMBean.
							getSizeExceededCount());
					Assert.assertEquals(
						successEventFired.get() + virusFoundEventFired.get(),
						antivirusAsyncStatisticsManagerMBean.
							getTotalScannedCount());
					Assert.assertEquals(
						virusFoundEventFired.get(),
						antivirusAsyncStatisticsManagerMBean.
							getVirusFoundCount());
				});
		}
		finally {
			antivirusScannerServiceRegistration.unregister();
			eventListenerServiceRegistration.unregister();
			schedulerHelperServiceRegistration.unregister();
		}
	}

	private AntivirusAsyncEventListener _create(
		Map<AntivirusAsyncEvent, Runnable> runnables) {

		return message -> {
			AntivirusAsyncEvent antivirusAsyncEvent =
				(AntivirusAsyncEvent)message.get("antivirusAsyncEvent");

			Runnable runnable = runnables.get(antivirusAsyncEvent);

			runnable.run();
		};
	}

	private SafeCloseable _sync() {
		Destination destination = MessageBusUtil.getDestination(
			AntivirusAsyncDestinationNames.ANTIVIRUS);

		Object originalExecutor = ReflectionTestUtil.getAndSetFieldValue(
			destination, "_noticeableThreadPoolExecutor",
			_syncNoticeableThreadPoolExecutor);

		return new SafeCloseable() {

			@Override
			public void close() {
				ReflectionTestUtil.setFieldValue(
					destination, "_noticeableThreadPoolExecutor",
					originalExecutor);
			}

		};
	}

	private void _withAsyncAntivirusConfiguration(
			int maximumQueueSize, int retryInterval, boolean sync,
			UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					AntivirusAsyncConfiguration.class.getName(),
					HashMapDictionaryBuilder.<String, Object>put(
						"maximumQueueSize", maximumQueueSize
					).put(
						"retryInterval", retryInterval
					).build())) {

			if (sync) {
				try (SafeCloseable safeCloseable = _sync()) {
					unsafeRunnable.run();
				}
			}
			else {
				unsafeRunnable.run();
			}
		}
	}

	private static BundleContext _bundleContext;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private MessageBus _messageBus;

	private final NoticeableThreadPoolExecutor
		_syncNoticeableThreadPoolExecutor = new NoticeableThreadPoolExecutor(
			1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1),
			Executors.defaultThreadFactory(),
			new ThreadPoolExecutor.AbortPolicy(),
			new ThreadPoolHandlerAdapter()) {

			@Override
			public void execute(Runnable runnable) {
				runnable.run();
			}

		};

	private class MockAntivirusScanner implements AntivirusScanner {

		public MockAntivirusScanner(
			UnsafeRunnable<AntivirusScannerException> unsafeRunnable) {

			_unsafeRunnable = unsafeRunnable;
		}

		@Override
		public boolean isActive() {
			return true;
		}

		@Override
		public void scan(byte[] bytes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void scan(File file) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void scan(InputStream inputStream)
			throws AntivirusScannerException {

			_unsafeRunnable.run();
		}

		private final UnsafeRunnable<AntivirusScannerException> _unsafeRunnable;

	}

}