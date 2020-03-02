package transformer.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.JakartaTransformProperties;
import com.ibm.ws.jakarta.transformer.JakartaTransformer;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.ClassAction;
import com.ibm.ws.jakarta.transformer.action.ClassChanges;
import com.ibm.ws.jakarta.transformer.action.impl.ClassActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.InputBufferImpl;
import com.ibm.ws.jakarta.transformer.action.impl.JarActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.LoggerImpl;
import com.ibm.ws.jakarta.transformer.action.impl.SelectionRuleImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ServiceConfigActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.SignatureRuleImpl;
import com.ibm.ws.jakarta.transformer.util.FileUtils;
import com.ibm.ws.jakarta.transformer.util.InputStreamData;

import transformer.test.data.Sample_InjectAPI_Jakarta;
import transformer.test.data.Sample_InjectAPI_Javax;
import transformer.test.util.ClassData;

public class TestTransformClass {

	public LoggerImpl createLogger(PrintStream printStream, boolean isTerse, boolean isVerbose) {
		return new LoggerImpl(printStream, isTerse, isVerbose);
	}

	public InputBufferImpl createBuffer() {
		return new InputBufferImpl();
	}

	public SelectionRuleImpl createSelectionRule(
		LoggerImpl logger,
		Set<String> useIncludes,
		Set<String> useExcludes) {

		return new SelectionRuleImpl( logger, useIncludes, useExcludes );
	}

	public SignatureRuleImpl createSignatureRule(
		LoggerImpl logger,
		Map<String, String> usePackageRenames,
		Map<String, String> usePackageVersions,
		Map<String, BundleData> bundleData,
		Map<String, String> directStrings) {

		return new SignatureRuleImpl( logger, usePackageRenames, usePackageVersions, bundleData, directStrings );
	}

	//

	@Test
	public void testJavaxAsJavax_inject() {
		System.out.println("test null conversion javax class load");
		testLoad( JAVAX_CLASS_NAME, getClassLoader_null() );
	}

	@Test
	public void testJakartaAsJakarta_inject() {
		System.out.println("test null conversion jakarta class load");
		testLoad( JAKARTA_CLASS_NAME, getClassLoader_null() );
	}

	@Test
	public void testJavaxAsJakarta_inject() {
		System.out.println("test javax to jakarta class load");
		Class<?> testClass = testLoad( JAVAX_CLASS_NAME, getClassLoader_toJakarta() );
		ClassData testData = new ClassData(testClass);
		testData.log( new PrintWriter(System.out, true) ); // autoflush
	}

	@Test
	public void testJakartaAsJavax_inject() {
		System.out.println("test jakarta to javax class load");
		Class<?> testClass = testLoad( JAKARTA_CLASS_NAME, getClassLoader_toJavax() );
		ClassData testData = new ClassData(testClass);
		testData.log( new PrintWriter(System.out, true) ); // autoflush
	}

	//

	public static final String JAVAX_CLASS_NAME = Sample_InjectAPI_Javax.class.getName();
	public static final String JAKARTA_CLASS_NAME = Sample_InjectAPI_Jakarta.class.getName();

	protected Set<String> includes;
	
	public Set<String> getIncludes() {
		if ( includes == null ) {
			includes = new HashSet<String>();
			includes.add( Action.classNameToBinaryTypeName(JAVAX_CLASS_NAME) );
			includes.add( Action.classNameToBinaryTypeName(JAKARTA_CLASS_NAME) );
		}

		return includes;
	}

	public Set<String> getExcludes() {
		return Collections.emptySet();
	}

	protected Map<String, String> packageRenames;

	public static final String JAVAX_INJECT_PACKAGE_NAME = "javax.inject";
	public static final String JAKARTA_INJECT_PACKAGE_NAME = "jakarta.inject";
	
	public Map<String, String> getPackageRenames() {
		if ( packageRenames == null ) {
			packageRenames = new HashMap<String, String>();
			packageRenames.put(
				Action.classNameToBinaryTypeName(JAVAX_INJECT_PACKAGE_NAME),
				Action.classNameToBinaryTypeName(JAKARTA_INJECT_PACKAGE_NAME) );
		}
		return packageRenames;
	}

	public ClassLoader getClassLoader_null() {
		return getClass().getClassLoader();
	}

	public JarActionImpl jakartaJarAction;
	public JarActionImpl javaxJarAction;

	public JarActionImpl getJakartaJarAction() {
		if ( jakartaJarAction == null ) {
			LoggerImpl logger = createLogger( System.out, !LoggerImpl.IS_TERSE, LoggerImpl.IS_VERBOSE );

			jakartaJarAction = new JarActionImpl(
				logger,
				createBuffer(),
				createSelectionRule( logger, getIncludes(), getExcludes() ),
				createSignatureRule( logger, getPackageRenames(), null, null, null ) );
		}

		return jakartaJarAction;
	}

	public JarActionImpl getJavaxJarAction() {
		if ( javaxJarAction == null ) {
			LoggerImpl logger = createLogger( System.out, !LoggerImpl.IS_TERSE, LoggerImpl.IS_VERBOSE );

			Map<String, String> invertedRenames =
				JakartaTransformProperties.invert( getPackageRenames() );

			javaxJarAction = new JarActionImpl(
				logger,
				createBuffer(),
				createSelectionRule( logger, getIncludes(), getExcludes() ),
				createSignatureRule( logger, invertedRenames, null, null, null ) );
		}

		return javaxJarAction;
	}

	public ClassLoader getClassLoader_toJakarta() {
		JarActionImpl jarAction = getJakartaJarAction();
		ClassActionImpl classAction = jarAction.addUsing( ClassActionImpl::new );
		ServiceConfigActionImpl configAction = jarAction.addUsing( ServiceConfigActionImpl::new );

		return new TransformClassLoader(
			getClass().getClassLoader(),
			jarAction, classAction, configAction );
	}

	public ClassLoader getClassLoader_toJavax() {
		JarActionImpl jarAction = getJavaxJarAction();
		ClassActionImpl classAction = jarAction.addUsing( ClassActionImpl::new );
		ServiceConfigActionImpl configAction = jarAction.addUsing( ServiceConfigActionImpl::new );

		return new TransformClassLoader(
			getClass().getClassLoader(),
			jarAction, classAction, configAction );
	}

	public Class<?> testLoad(String className, ClassLoader classLoader) {
		System.out.println("Loading [ " + className + " ] using [ " + classLoader + " ]");

		@SuppressWarnings("unused")
		Class<?> objectClass;
		try {
			objectClass = classLoader.loadClass( java.lang.Object.class.getName() );
		} catch ( Throwable th ) {
			th.printStackTrace(System.out);
			Assertions.fail("Failed to load class [ " + java.lang.Object.class.getName() + " ]: " + th);
			return null;
		}

		Class<?> testClass;
		try {
			testClass = classLoader.loadClass(className);
		} catch ( ClassNotFoundException e ) {
			e.printStackTrace(System.out);
			Assertions.fail("Failed to load class [ " + className + " ]: " + e);
			return null;
		} catch ( Throwable th ) {
			th.printStackTrace(System.out);
			Assertions.fail("Failed to load class [ " + className + " ]: " + th);
			return null;
		}

		System.out.println("Loaded [ " + className + " ]: " + testClass);
		return testClass;
	}

	public static InputStream getResourceStream(String resourceName) throws IOException {
		InputStream inputStream = TestUtils.getResourceStream(resourceName);
		if ( inputStream == null ) {
			throw new IOException("Resource not found [ " + resourceName + " ]");
		}
		return inputStream;
	}

	public static final String TEST_DATA_RESOURCE_NAME = "transformer/test/data";
	public static final String ANNOTATED_SERVLET_RESOURCE_NAME = "AnnotatedServlet.class";

	public static final String TRANSFORMER_RESOURCE_NAME = "com/ibm/ws/jakarta/transformer";

	public static Map<String, String> getStandardRenames() throws IOException {
		String transformerResourceName = JakartaTransformer.class.getPackage().getName().replace('.', '/');
		String renamesResourceName = transformerResourceName + '/' + JakartaTransformer.DEFAULT_RENAMES_REFERENCE;

		InputStream renamesInputStream = getResourceStream(renamesResourceName); // throws IOException
		Reader renamesReader = new InputStreamReader(renamesInputStream);

		Properties renameProperties = new Properties();
		renameProperties.load(renamesReader); // throws IOException

		Map<String, String> renames = new HashMap<String, String>( renameProperties.size() );
		for ( Map.Entry<Object, Object> renameEntry : renameProperties.entrySet() ) {
			String initialPackageName = (String) renameEntry.getKey(); 
			String finalPackageName = (String) renameEntry.getValue();
			renames.put(initialPackageName, finalPackageName);
		}

		return renames;
	}

	public ClassAction createStandardClassAction() throws IOException {
		LoggerImpl logger =
			createLogger( System.out, !LoggerImpl.IS_TERSE, LoggerImpl.IS_VERBOSE );

		return new ClassActionImpl(
			logger,
			createBuffer(),
			createSelectionRule( logger, Collections.emptySet(), Collections.emptySet() ),
			createSignatureRule( logger, getStandardRenames(), null, null, null ) );
		// 'getStandardRenames' throws IOException
	}

	@Test
	public void testAnnotatedServlet() throws JakartaTransformException, IOException {
		ClassAction classAction = createStandardClassAction(); // throws IOException

		String resourceName = TEST_DATA_RESOURCE_NAME + '/' + ANNOTATED_SERVLET_RESOURCE_NAME;
		InputStream inputStream = getResourceStream(resourceName); // throws IOException

		InputStreamData outputStreamData = classAction.apply(resourceName, inputStream); // throws JakartaTransformException
		display( classAction.getChanges() );

		OutputStream outputStream = new FileOutputStream("build" + '/' + ANNOTATED_SERVLET_RESOURCE_NAME); // throws FileNotFoundException
		try {
			FileUtils.transfer(outputStreamData.stream, outputStream); // throws IOException
		} finally {
			outputStream.close(); // throws IOException
		}
	}

	public void display(ClassChanges classChanges) {
		System.out.println("Input class [ " + classChanges.getInputClassName() + " ]");
		System.out.println("Output class [ " + classChanges.getOutputClassName() + " ]");

		System.out.println("Input super class [ " + classChanges.getInputSuperName() + " ]");
		System.out.println("Output super class [ " + classChanges.getOutputSuperName() + " ]");
		
		System.out.println("Modified interfaces [ " + classChanges.getModifiedInterfaces() + " ]");
		System.out.println("Modified fields [ " + classChanges.getModifiedFields() + " ]");
		System.out.println("Modified methods [ " + classChanges.getModifiedMethods() + " ]");
		System.out.println("Modified constants [ " + classChanges.getModifiedConstants() + " ]");
		System.out.println("Modified attributes [ " + classChanges.getModifiedAttributes() + " ]");
	}

	//

	public static final Map<String, String> DIRECT_STRINGS;
	static {
		DIRECT_STRINGS = new HashMap<String, String>();
		DIRECT_STRINGS.put("DIRECT_1", "DIRECT_X"); // String value update // 2 hits
		DIRECT_STRINGS.put("DIRECT21", "DIRECT21"); // Variable name update // 2 hits

		DIRECT_STRINGS.put("Sample value 1", "Sample value 2"); // String constant reference update // 1 hit
		DIRECT_STRINGS.put("Sample", "Official"); // String constant reference update (not found) // 0 hits
		DIRECT_STRINGS.put("value", "product"); // String constant reference update (not found) // 0 hits
	}

	public static final Map<String, String> getDirectStrings() {
		return DIRECT_STRINGS;
	}

	public ClassActionImpl createDirectClassAction() {
		LoggerImpl logger =
			createLogger( System.out, !LoggerImpl.IS_TERSE, LoggerImpl.IS_VERBOSE );

		return new ClassActionImpl(
			logger,
			createBuffer(),
			createSelectionRule( logger, Collections.emptySet(), Collections.emptySet() ),
			createSignatureRule( logger, Collections.emptyMap(), null, null, getDirectStrings() ) );
	}

	public static final String DIRECT_STRINGS_RESOURCE_NAME = "Sample_DirectStrings.class";

	@Test
	public void testDirectStrings() throws JakartaTransformException, IOException {
		ClassAction classAction = createDirectClassAction();

		String resourceName = TEST_DATA_RESOURCE_NAME + '/' + DIRECT_STRINGS_RESOURCE_NAME;
		InputStream inputStream = getResourceStream(resourceName); // throws IOException

		@SuppressWarnings("unused")
		InputStreamData outputStreamData = classAction.apply(resourceName, inputStream); // throws JakartaTransformException
		display( classAction.getChanges() );

		int expectedChanges = 5;
		int actualChanges = classAction.getChanges().getModifiedConstants(); 
		Assertions.assertEquals(
			expectedChanges, actualChanges, 
			"Incorrect count of constant changes");
	}
}
