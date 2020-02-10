package transformer.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ibm.ws.jakarta.transformer.JakartaTransformException;
import com.ibm.ws.jakarta.transformer.JakartaTransformProperties;
import com.ibm.ws.jakarta.transformer.action.ServiceConfigAction;
import com.ibm.ws.jakarta.transformer.action.impl.ServiceConfigActionImpl;
import com.ibm.ws.jakarta.transformer.util.InputStreamData;

public class TestTransformServiceConfig {
	
	public static final String JAVAX_OTHER_READER_SERVICE_PATH = "transformer/test/data/META-INF/services/javax.other.Reader";
	public static final String[] JAVAX_OTHER_READER_LINES = { "javax.other.ReaderImpl" };
	public static final String JAVAX_SAMPLE_READER_SERVICE_PATH = "transformer/test/data/META-INF/services/javax.sample.Reader";
	public static final String[] JAVAX_SAMPLE_READER_LINES = { "javax.sample.ReaderImpl" };	
	public static final String JAVAX_SAMPLE_WRITER_SERVICE_PATH = "transformer/test/data/META-INF/services/javax.sample.Writer";
	public static final String[] JAVAX_SAMPLE_WRITER_LINES = { "javax.sample.WriterImpl" };	
	
	public static final String JAKARTA_OTHER_READER_SERVICE_PATH = "transformer/test/data/META-INF/services/jakarta.other.Reader";
	public static final String[] JAKARTA_OTHER_READER_LINES = { "jakarta.other.ReaderImpl" };
	public static final String JAKARTA_SAMPLE_READER_SERVICE_PATH = "transformer/test/data/META-INF/services/jakarta.sample.Reader";
	public static final String[] JAKARTA_SAMPLE_READER_LINES = { "jakarta.sample.ReaderImpl" };	
	public static final String JAKARTA_SAMPLE_WRITER_SERVICE_PATH = "transformer/test/data/META-INF/services/jakarta.sample.Writer";
	public static final String[] JAKARTA_SAMPLE_WRITER_LINES = { "jakarta.sample.WriterImpl" };	

	public static final String JAVAX_SAMPLE = "javax.sample";
	public static final String JAKARTA_SAMPLE = "jakarta.sample";
	
	public static final String JAVAX_SERVLET = "javax.servlet";
	public static final String JAVAX_SERVLET_ANNOTATION = "javax.servlet.annotation";
	public static final String JAVAX_SERVLET_DESCRIPTOR = "javax.servlet.descriptor";
	public static final String JAVAX_SERVLET_HTTP = "javax.servlet.http";
	public static final String JAVAX_SERVLET_RESOURCES = "javax.servlet.resources";	

	public static final String JAKARTA_SERVLET_VERSION = "[2.6, 6.0)";
	public static final String JAKARTA_SERVLET_ANNOTATION_VERSION  = "[2.6, 6.0)";
	public static final String JAKARTA_SERVLET_DESCRIPTOR_VERSION  = "[2.6, 6.0)";
	public static final String JAKARTA_SERVLET_HTTP_VERSION  = "[2.6, 6.0)";
	public static final String JAKARTA_SERVLET_RESOURCES_VERSION  = "[2.6, 6.0)";

	protected Set<String> includes;
	
	public Set<String> getIncludes() {
		if ( includes == null ) {
			includes = new HashSet<String>();
			includes.add(JAVAX_SAMPLE_READER_SERVICE_PATH);
			includes.add(JAVAX_SAMPLE_WRITER_SERVICE_PATH);
		}

		return includes;
	}

	public Set<String> getExcludes() {
		return Collections.emptySet();
	}

	protected Map<String, String> packageRenames;

	public Map<String, String> getPackageRenames() {
		if ( packageRenames == null ) {
			packageRenames = new HashMap<String, String>();
			packageRenames.put(JAVAX_SAMPLE, JAKARTA_SAMPLE);
		}
		return packageRenames;
	}
	
	public ServiceConfigActionImpl jakartaServiceAction;
	public ServiceConfigActionImpl javaxServiceAction;

	public ServiceConfigActionImpl getJakartaServiceAction() {
		if ( jakartaServiceAction == null ) {
			jakartaServiceAction = new ServiceConfigActionImpl(
				getIncludes(), getExcludes(),
				getPackageRenames(), null, null );
		}
		return jakartaServiceAction;
	}

	public ServiceConfigActionImpl getJavaxServiceAction() {
		if ( javaxServiceAction == null ) {
			Map<String, String> invertedRenames = JakartaTransformProperties.invert( getPackageRenames() );
			javaxServiceAction = new ServiceConfigActionImpl(
				getIncludes(), getExcludes(),
				invertedRenames, null, null );
		}
		return javaxServiceAction;
	}

	@Test
	public void testJakartaTransform() throws IOException, JakartaTransformException {
		ServiceConfigAction jakartaAction = getJakartaServiceAction();

		verifyTransform(
			jakartaAction,
			JAVAX_OTHER_READER_SERVICE_PATH,
			JAVAX_OTHER_READER_LINES); // Not transformed 
		verifyTransform(
			jakartaAction,
			JAVAX_SAMPLE_READER_SERVICE_PATH,
			JAKARTA_SAMPLE_READER_LINES); // Transformed
		verifyTransform(
			jakartaAction,
			JAVAX_SAMPLE_READER_SERVICE_PATH,
			JAKARTA_SAMPLE_READER_LINES); // Transformed 
	}

	@Test
	public void testJavaxTransform() throws IOException, JakartaTransformException {
		ServiceConfigAction javaxAction = getJavaxServiceAction();

		verifyTransform(
			javaxAction,
			JAKARTA_OTHER_READER_SERVICE_PATH,
			JAKARTA_OTHER_READER_LINES); // Not transformed
		verifyTransform(
			javaxAction,
			JAKARTA_SAMPLE_READER_SERVICE_PATH,
			JAVAX_SAMPLE_READER_LINES); // Transformed
		verifyTransform(
			javaxAction,
			JAKARTA_SAMPLE_READER_SERVICE_PATH,
			JAVAX_SAMPLE_READER_LINES); // Transformed
	}

	protected void verifyTransform(
		ServiceConfigAction action,
		String inputName,
		String[] expectedLines) throws IOException, JakartaTransformException {

		InputStream inputStream = TestUtils.getResourceStream(inputName);

		InputStreamData transformedData;
		try {
			transformedData = action.apply(inputName, inputStream);
		} finally {
			inputStream.close();
		}

		List<String> transformedLines = TestUtils.loadLines(transformedData.stream);
		TestUtils.filter(transformedLines);
		TestUtils.verify(inputName, expectedLines, transformedLines);
	}
	
}
