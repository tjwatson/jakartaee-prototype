package transformer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ibm.ws.jakarta.transformer.JakartaTransformer;
import com.ibm.ws.jakarta.transformer.JakartaTransformer.TransformOptions;
import com.ibm.ws.jakarta.transformer.action.impl.JavaActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ManifestActionImpl;

class TestCommandLine {
    
    String currentDirectory = ".";
    final String DATA_DIR = "src/test/data/";

    @BeforeEach
    public void setUp() {
        currentDirectory = System.getProperty("user.dir");
        System.out.println("setUp: Current directory is: [" + currentDirectory + "]");
    }

    @Test
    void testManifestActionAccepted() throws Exception {   
        String inputFileName = DATA_DIR + "MANIFEST.MF";
        String outputFileName = DATA_DIR + "output_MANIFEST.MF";
        verifyAction(ManifestActionImpl.class.getName(), inputFileName, outputFileName);
    }

    @Test
    void testJavaActionAccepted() throws Exception {
        String inputFileName = DATA_DIR + "A.java";
        String outputFileName = DATA_DIR + "output_A.java";
        verifyAction(JavaActionImpl.class.getName(), inputFileName, outputFileName);
    }

    private void verifyAction(
    	String actionClassName,
    	String inputFileName, String outputFileName) throws Exception {

        JakartaTransformer t = new JakartaTransformer(System.out, System.err);

        String[] args = new String[] { inputFileName, "-o"};

        t.setArgs(args);
        t.setParsedArgs();

        TransformOptions options = t.getTransformOptions();

        // SET INPUT
        assertTrue(options.setInput(), "options.setInput() failed");
        assertEquals(inputFileName, options.getInputFileName(), 
        	"input file name is not correct [" + options.getInputFileName() + "]");

        // SET OUTPUT
        assertTrue(options.setOutput(), "options.setOutput() failed");
        assertEquals(outputFileName, options.getOutputFileName(), 
        	"output file name is not correct [" + options.getOutputFileName() + "]");

        assertTrue(options.setRules(), "options.setRules() failed");
        assertTrue(options.acceptAction(), "options.acceptAction() failed");
        assertEquals(actionClassName, options.acceptedAction.getClass().getName());

        options.transform();
        assertTrue((new File(outputFileName)).exists(), "output file not created");            }
}
