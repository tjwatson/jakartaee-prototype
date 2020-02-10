package transformer.test;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.ibm.ws.jakarta.transformer.JakartaTransformProperties;
import com.ibm.ws.jakarta.transformer.action.Action;
import com.ibm.ws.jakarta.transformer.action.impl.ClassActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.JarActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ServiceConfigActionImpl;

import transformer.test.data.Sample_InjectAPI_Jakarta;
import transformer.test.data.Sample_InjectAPI_Javax;
import transformer.test.util.ClassData;

public class TestTransformClass {

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
			jakartaJarAction = new JarActionImpl(
				getIncludes(), getExcludes(),
				getPackageRenames(), null, null );
		}
		return jakartaJarAction;
	}

	public JarActionImpl getJavaxJarAction() {
		if ( javaxJarAction == null ) {
			Map<String, String> invertedRenames = JakartaTransformProperties.invert( getPackageRenames() );
			javaxJarAction = new JarActionImpl(
				getIncludes(), getExcludes(),
				invertedRenames, null, null );
		}
		return javaxJarAction;
	}

	public ClassLoader getClassLoader_toJakarta() {
		JarActionImpl jarAction = getJakartaJarAction();
		ClassActionImpl classAction = new ClassActionImpl(jarAction);
		ServiceConfigActionImpl configAction = new ServiceConfigActionImpl(jarAction);

		return new TransformClassLoader(
			getClass().getClassLoader(),
			jarAction, classAction, configAction );
	}

	public ClassLoader getClassLoader_toJavax() {
		JarActionImpl jarAction = getJavaxJarAction();
		ClassActionImpl classAction = new ClassActionImpl(jarAction);
		ServiceConfigActionImpl configAction = new ServiceConfigActionImpl(jarAction);

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
}
