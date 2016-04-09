/*
 * Copyright 2016 Harald Wellmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.exam.testng.listener;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ops4j.pax.exam.Constants;
import org.ops4j.pax.exam.ExamConfigurationException;
import org.ops4j.pax.exam.ExceptionHelper;
import org.ops4j.pax.exam.TestAddress;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.exam.TestDirectory;
import org.ops4j.pax.exam.TestInstantiationInstruction;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.spi.ExamReactor;
import org.ops4j.pax.exam.spi.StagedExamReactor;
import org.ops4j.pax.exam.spi.reactors.ReactorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.MethodInstance;

/**
 * TestNG listener delegate running directly under the driver.
 *
 * @author Harald Wellmann
 * @since 5.0.0
 */
class DriverListener implements ISuiteListener, IMethodInterceptor, IHookable {

    private static final Logger LOG = LoggerFactory.getLogger(DriverListener.class);

    /**
     * Staged reactor for this test class. This may actually be a reactor already staged for a
     * previous test class, depending on the reactor strategy.
     */
    private StagedExamReactor stagedReactor;

    /**
     * Maps method names to test addresses. The method names are qualified by class and container
     * names. Each method of the test class is cloned for each container.
     */
    private Map<String, TestAddress> methodToAddressMap = new LinkedHashMap<String, TestAddress>();

    /**
     * Reactor manager singleton.
     */
    private ReactorManager manager;

    /**
     * Shall we use a probe invoker, or invoke test methods directly?
     */
    private boolean useProbeInvoker;

    private List<ITestNGMethod> methods;

    /**
     * Called by TestNG before the suite starts. When running in the container, this is a no op.
     * Otherwise, we create and stage the reactor.
     *
     * @param suite
     *            test suite
     */
    @Override
    public void onStart(ISuite suite) {
        manager = ReactorManager.getInstance();
        stagedReactor = stageReactor(suite);
        manager.beforeSuite(stagedReactor);
    }

    /**
     * Called by TestNG after the suite has finished. When running in the container, this is a no
     * op. Otherwise, we stop the reactor.
     *
     * @param suite
     *            test suite
     */
    @Override
    public void onFinish(ISuite suite) {
        manager.afterSuite(stagedReactor);
    }

    public void onBeforeClass(ITestClass testClass, IMethodInstance mi) {
        Object testClassInstance = mi.getInstance();
        Class<?> klass = testClassInstance.getClass();
        stagedReactor = stageReactorForClass(klass, testClassInstance);
        if (!useProbeInvoker) {
            manager.inject(testClassInstance);
        }
        manager.beforeClass(stagedReactor, testClassInstance);
    }

    /**
     * @param testClass
     * @param mi
     */
    public void onAfterClass(ITestClass testClass, IMethodInstance mi) {
        manager.afterClass(stagedReactor, testClass.getRealClass());
    }

    /**
     * Stages the reactor. This involves building the probe including all test methods of the suite
     * and creating one or more test containers.
     * <p>
     * When using a probe invoker, we register the tests with the reactor.
     *
     * @param suite
     *            test suite
     * @return staged reactor
     */
    private synchronized StagedExamReactor stageReactor(ISuite suite) {
        try {
            methods = suite.getAllMethods();
            Class<?> testClass = methods.get(0).getRealClass();
            LOG.debug("test class = {}", testClass);
            Object testClassInstance = testClass.newInstance();
            return stageReactorForClass(testClass, testClassInstance);
        }
        catch (InstantiationException | IllegalAccessException exc) {
            throw new TestContainerException(exc);
        }
    }

    private StagedExamReactor stageReactorForClass(Class<?> testClass, Object testClassInstance) {
        try {
            ExamReactor examReactor = manager.prepareReactor(testClass, testClassInstance);
            useProbeInvoker = !manager.getSystemType().equals(Constants.EXAM_SYSTEM_CDI);
            if (useProbeInvoker) {
                addTestsToReactor(examReactor, testClassInstance, methods);
            }
            return manager.stageReactor();
        }
        catch (IOException | ExamConfigurationException exc) {
            throw new TestContainerException(exc);
        }
    }

    /**
     * Adds all tests of the suite to the reactor and creates a probe builder.
     * <p>
     * TODO This driver currently assumes that all test classes of the suite use the default probe
     * builder. It builds one probe containing all tests of the suite. This is why the
     * testClassInstance argument is just an arbitrary instance of one of the classes of the suite.
     *
     * @param reactor
     *            unstaged reactor
     * @param testClassInstance
     *            not used
     * @param testMethods
     *            all methods of the suite.
     * @throws IOException
     * @throws ExamConfigurationException
     */
    private void addTestsToReactor(ExamReactor reactor, Object testClassInstance,
        List<ITestNGMethod> testMethods) throws IOException, ExamConfigurationException {
        TestProbeBuilder probe = manager.createProbeBuilder(testClassInstance);
        for (ITestNGMethod m : testMethods) {
            TestAddress address = probe.addTest(m.getRealClass(), m.getMethodName());
            manager.storeTestMethod(address, m);
        }
        reactor.addProbe(probe);
    }

    /**
     * Runs a test method under the driver.
     * <p>
     * Fires beforeClass and afterClass events when the current class changes, as we do not get
     * these events from TestNG. This requires the test methods to be sorted by class, see
     * {@link #intercept(List, ITestContext)}.
     * <p>
     * When using a probe invoker, we delegate the test method invocation to the invoker so that the
     * test will be executed in the container context.
     * <p>
     * Otherwise, we directly run the test method.
     *
     * @param callBack
     *            TestNG callback for test method
     * @param testResult
     *            test result container
     * @throws ExamConfigurationException
     * @throws IOException
     */
    @Override
    public void run(IHookCallBack callBack, ITestResult testResult) {
        LOG.info("running {}", testResult.getName());

        if (!useProbeInvoker) {
            callBack.runTestMethod(testResult);
            return;
        }

        TestAddress address = methodToAddressMap.get(testResult.getName());

        try {
            stagedReactor.invoke(address);
            testResult.setStatus(ITestResult.SUCCESS);
        }
        // CHECKSTYLE:SKIP : StagedExamReactor API
        catch (Exception e) {
            Throwable t = ExceptionHelper.unwind(e);
            LOG.error("Exception", e);
            testResult.setStatus(ITestResult.FAILURE);
            testResult.setThrowable(t);
        }
    }

    /**
     * Callback from TestNG which lets us manipulate the list of test methods in the suite. When
     * running under the driver and using a probe invoker, we now construct the test addresses to be
     * used be the probe invoker, and we sort the methods by class to make sure we can fire
     * beforeClass and afterClass events later on.
     */
    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> testMethods,
        ITestContext context) {
        if (!useProbeInvoker) {
            return testMethods;
        }

        boolean mangleMethodNames = manager.getNumConfigurations() > 1;
        TestDirectory testDirectory = TestDirectory.getInstance();
        List<IMethodInstance> newInstances = new ArrayList<IMethodInstance>();
        Set<TestAddress> targets = stagedReactor.getTargets();
        for (TestAddress address : targets) {
            ITestNGMethod frameworkMethod = (ITestNGMethod) manager
                .lookupTestMethod(address.root());
            if (frameworkMethod == null) {
                continue;
            }
            Method javaMethod = frameworkMethod.getConstructorOrMethod().getMethod();

            if (mangleMethodNames) {
                frameworkMethod = new ReactorTestNGMethod(frameworkMethod, javaMethod, address);
            }

            MethodInstance newInstance = new MethodInstance(frameworkMethod);
            newInstances.add(newInstance);
            methodToAddressMap.put(frameworkMethod.getMethodName(), address);
            testDirectory.add(address, new TestInstantiationInstruction(
                frameworkMethod.getRealClass().getName() + ";" + javaMethod.getName()));

        }
        return newInstances;
    }
}
