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
import com.liferay.antivirus.async.store.retry.AntivirusAsyncRetryScheduler;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.antivirus.AntivirusScanner;
import com.liferay.document.library.kernel.antivirus.AntivirusScannerException;
import com.liferay.document.library.kernel.antivirus.AntivirusVirusFoundException;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.document.library.test.util.DLTestUtil;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.util.Dictionary;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.osgi.framework.ServiceRegistration;

/**
 * @author Raymond Augé
 */
@RunWith(Arquillian.class)
public class AsyncAntivirusEventTest {

	@ClassRule
	@Rule
	public static final LiferayIntegrationTestRule liferayIntegrationTestRule =
		new LiferayIntegrationTestRule();

	@BeforeClass
	public static void setUpClass() {
		Bundle bundle = FrameworkUtil.getBundle(AsyncAntivirusEventTest.class);

		_bundleContext = bundle.getBundleContext();
	}

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testMissing() throws Exception {
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
				new AntivirusAsyncEventListenerBuilder().register(
					AntivirusAsyncEvent.MISSING, () -> missingFired.set(true)
				).build(),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1,
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
	public void testProcessingError() throws Exception {
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
				new AntivirusAsyncEventListenerBuilder().register(
					AntivirusAsyncEvent.PREPARE,
					() -> prepareEventFired.set(true)
				).register(
					AntivirusAsyncEvent.PROCESSING_ERROR,
					() -> processingErrorEventFired.set(true)
				).build(),
				MapUtil.singletonDictionary(Constants.SERVICE_RANKING, -100));

		try {
			_withAsyncAntivirusConfiguration(
				1, 1,
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
	public void testSizeExceeded() throws Exception {
		AtomicBoolean prepareEventFired = new AtomicBoolean();
		AtomicBoolean sizeExceededFired = new AtomicBoolean();

		AtomicBoolean scannerWasCalled = new AtomicBoolean();

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
				new AntivirusAsyncEventListenerBuilder().register(
					AntivirusAsyncEvent.PREPARE,
					() -> prepareEventFired.set(true)
				).register(
					AntivirusAsyncEvent.SIZE_EXCEEDED,
					() -> sizeExceededFired.set(true)
				).build(),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1,
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
	public void testSuccess() throws Exception {
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
				new AntivirusAsyncEventListenerBuilder().register(
					AntivirusAsyncEvent.PREPARE,
					() -> prepareEventFired.set(true)
				).register(
					AntivirusAsyncEvent.SUCCESS, () -> successFired.set(true)
				).build(),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1,
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
	public void testVirusFound() throws Exception {
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
				new AntivirusAsyncEventListenerBuilder().register(
					AntivirusAsyncEvent.PREPARE,
					() -> prepareEventFired.set(true)
				).register(
					AntivirusAsyncEvent.VIRUS_FOUND,
					() -> virusFoundFired.set(true)
				).build(),
				null);

		try {
			_withAsyncAntivirusConfiguration(
				1, 1,
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

	private void _withAsyncAntivirusConfiguration(
			int maximumQueueSize, int retryInterval,
			UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		Dictionary<String, Object> dictionary =
			HashMapDictionaryBuilder.<String, Object>put(
				"maximumQueueSize", maximumQueueSize
			).put(
				"retryInterval", retryInterval
			).build();

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					AntivirusAsyncConfiguration.class.getName(), dictionary);
			SafeCloseable safeCloseable = SyncDestinationUtil.sync()) {

			unsafeRunnable.run();
		}
	}

	private static BundleContext _bundleContext;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private MessageBus _messageBus;

}