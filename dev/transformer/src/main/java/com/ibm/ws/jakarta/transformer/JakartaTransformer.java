package com.ibm.ws.jakarta.transformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.impl.ActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ClassActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.CompositeActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.DirectoryActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.InputBufferImpl;
import com.ibm.ws.jakarta.transformer.action.impl.JarActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.LoggerImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ManifestActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.NullActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.SelectionRuleImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ServiceConfigActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.SignatureRuleImpl;
import com.ibm.ws.jakarta.transformer.util.FileUtils;

import aQute.lib.io.IO;
import aQute.lib.utf8properties.UTF8Properties;
import aQute.libg.uri.URIUtil;

public class JakartaTransformer {
    public static final int SUCCESS_RC = 0;
    public static final int PARSE_ERROR_RC = 1;
    public static final int RULES_ERROR_RC = 2;
    public static final int TRANSFORM_ERROR_RC = 3;
    public static final int ACTION_ERROR_RC = 4;

    public static void main(String[] args) throws Exception {
        JakartaTransformer jTrans =
            new JakartaTransformer(System.out, System.err);
        jTrans.setArgs(args);

        @SuppressWarnings("unused")
        int rc = jTrans.run();
        // System.exit(rc); // TODO: How should this code be returned?
    }

    //

    public static class OptionSettings {
        private static final boolean HAS_ARG = true;
        private static final boolean IS_REQUIRED = true;
        private static final String NO_GROUP = null;

        private OptionSettings (
            String shortTag, String longTag, String description,
            boolean hasArg,
            boolean isRequired, String groupTag) {

            this.shortTag = shortTag;
            this.longTag = longTag;
            this.description = description;

            this.isRequired = isRequired;

            this.hasArg = hasArg;
            this.groupTag = groupTag;
        }

        private final String shortTag;
        private final String longTag;
        private final String description;

        public String getShortTag() {
            return shortTag;
        }

        public String getLongTag() {
            return longTag;
        }

        public String getDescription() {
            return description;
        }

        //

        // Is this option required.
        // If in a group, is at least one of the group required.

        private final boolean isRequired;

        //

        private final boolean hasArg;
        private final String groupTag;

        public boolean getHasArg() {
            return hasArg;
        }

        public String getGroupTag() {
            return groupTag;
        }

        public boolean getIsRequired() {
            return isRequired;
        }

        //

        public static Options build(OptionSettings[] settings) {
            Options options = new Options();

            Map<String, OptionGroup> groups = new HashMap<String, OptionGroup>();

            for ( OptionSettings optionSettings : settings ) {
                String groupTag = optionSettings.getGroupTag();
                OptionGroup group;
                if ( groupTag != null ) {
                    group = groups.get(groupTag);
                    if ( group == null ) {
                        group = new OptionGroup();
                        if ( optionSettings.getIsRequired() ) {
                            group.setRequired(true);
                        }
                        groups.put(groupTag, group);

                        options.addOptionGroup(group);
                    }

                } else {
                    group = null;
                }

                Option option = Option.builder( optionSettings.getShortTag() )
                    .longOpt( optionSettings.getLongTag() )
                    .desc( optionSettings.getDescription() )
                    .hasArg( optionSettings.getHasArg() )
                    .required( (group == null) && optionSettings.getIsRequired() )
                    .build();

                if ( group != null ) {
                    group.addOption(option);
                } else {
                    options.addOption(option);
                }
            }

            return options;
        }
    }

    // Not in use, until option grouping is figured out.

    public static final String INPUT_GROUP = "input";
    public static final String LOGGING_GROUP = "logging";

    public static final String DEFAULT_SELECTIONS_REFERENCE = "jakarta-selections.properties";
    public static final String DEFAULT_RENAMES_REFERENCE = "jakarta-renames.properties";
    public static final String DEFAULT_VERSIONS_REFERENCE = "jakarta-versions.properties";
    public static final String DEFAULT_BUNDLES_REFERENCE = "jakarta-bundles.properties";

    public static enum AppOption {
        USAGE  ("u", "usage",    "Display usage",
            	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        HELP   ("h", "help",    "Display help",
            	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        TERSE  ("q", "quiet",   "Display quiet output",
        	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        VERBOSE("v", "verbose", "Display verbose output",
        	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),        

        RULES_SELECTIONS("ts", "selection", "Transformation selections URL",
        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        RULES_RENAMES("tr", "renames", "Transformation package renames URL",
        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        RULES_VERSIONS("tv", "versions", "Transformation package versions URL",
            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        RULES_BUNDLES("tb", "bundles", "Transformation bundle updates URL",
            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

        INVERT("i", "invert", "Invert transformation rules",
           	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

//      CLASS("c", "class", "Input class",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        MANIFEST("m", "manifest", "Input manifest",
//            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        FEATURE("f", "feature", "Input feature manifest",
//            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        SERVICE_CONFIG("s", "service config", "Input service configuration",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        XML("x", "xml", "Input XML",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

//        ZIP  ("z", "zip",   "Input zip archive",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        JAR  ("j", "jar",   "Input java archive",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        WAR  ("w", "war",   "Input web application archive",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        RAR  ("r", "rar",   "Input resource archive",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//        EAR  ("e", "ear",   "Input enterprise application archive",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
//
//        OUTPUT("o", "output", "Output file",
//        	OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

        ACTION("a", "action", "Action",
            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),
        OVERWRITE("o", "overwrite", "Overwrite",
            !OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

    	DRYRUN("d", "dryrun", "Dry run",
                !OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP);

        private AppOption(
            String shortTag, String longTag, String description, boolean hasArg,
            boolean isRequired, String groupTag) {

            this.settings = new OptionSettings(
                shortTag, longTag, description, hasArg,
                isRequired, groupTag);
        }

        private final OptionSettings settings;

        public OptionSettings getSettings() {
            return settings;
        }

        public String getShortTag() {
            return getSettings().getShortTag();
        }
        
        public String getLongTag() {
            return getSettings().getLongTag();
        }

        public String getDescription() {
            return getSettings().getDescription();
        }

        public boolean getIsRequired() {
            return getSettings().getIsRequired();
        }

        public boolean getHasArg() {
            return getSettings().getHasArg();
        }

        public String getGroupTag() {
            return getSettings().getGroupTag();
        }

        //

        private static OptionSettings[] getAllSettings() {
            AppOption[] allAppOptions =  AppOption.values();

            OptionSettings[] allSettings = new OptionSettings[ allAppOptions.length ];

            for ( int optionNo = 0; optionNo < allAppOptions.length; optionNo++ ) {
                allSettings[optionNo] = allAppOptions[optionNo].getSettings();
            }

            return allSettings;
        }

        public static Options build() {
            return OptionSettings.build( getAllSettings() );
        }
    }

    public JakartaTransformer(PrintStream infoStream, PrintStream errorStream) {
        this.infoStream = infoStream;
        this.errorStream = errorStream;

        this.appOptions = AppOption.build();
    }

    private final PrintStream infoStream;
    private final PrintStream errorStream;

    public PrintStream getInfoStream() {
        return infoStream;
    }

    public void info(String text, Object... parms) {
        getInfoStream().printf(text, parms);
    }

    public PrintStream getErrorStream() {
        return errorStream;
    }

    protected void error(String message, Object... parms) {
        getErrorStream().printf(message, parms);
    }

    protected void error(String message, Throwable th, Object... parms) {
        getErrorStream().printf(message, th.getMessage(), parms);
        th.printStackTrace( getErrorStream() );
    }

    private final Options appOptions;

    public Options getAppOptions() {
        return appOptions;
    }

    private String[] args;
    private CommandLine parsedArgs;

    protected void setArgs(String[] args) {
        this.args = args;
    }

    protected String[] getArgs() {
        return args;
    }

    protected void setParsedArgs() throws ParseException {
        CommandLineParser parser = new DefaultParser();
        this.parsedArgs = parser.parse( getAppOptions(), getArgs());
    }

    protected CommandLine getParsedArgs() {
        return parsedArgs;
    }
    
    /**
     * The input file name is the first command line argument
     * and is required.
     */
    protected String getInputFileName() {
    	String[] useArgs = parsedArgs.getArgs();
    	if ( useArgs != null ) {
        	if (useArgs.length > 0) {
        		return FileUtils.normalize(useArgs[0]);
        	} 
    	}
    	return null;
    }
    
    /**
     * The output file name is the second command line argument
     * and is optional.  If not specified, a default output file name
     * is created based on the input file name.
     */
    protected String getOutputFileName() {
        String[] useArgs = parsedArgs.getArgs();
        if ( useArgs != null ) {
            if (useArgs.length > 1) {
                return FileUtils.normalize(useArgs[1]);
            } 
        }

        info("Output file not specified.\n");

        final String OUTPUT_PREFIX = "output_";
        String inputFileName = getInputFileName();
        int indexOfLastSlash = inputFileName.lastIndexOf('/');
        if (indexOfLastSlash == -1 ) {
            return OUTPUT_PREFIX + inputFileName; 
        } else {
            return inputFileName.substring(0, indexOfLastSlash+1) + OUTPUT_PREFIX + inputFileName.substring(indexOfLastSlash+1);
        }
    }
    
    protected boolean hasOption(AppOption option) {
        return getParsedArgs().hasOption( option.getShortTag() );
    }
    
    protected String getOptionValue(AppOption option) {
        CommandLine useParsedArgs = getParsedArgs();
        String useShortTag = option.getShortTag();
        if ( useParsedArgs.hasOption(useShortTag) ) {
            return useParsedArgs.getOptionValue(useShortTag);
        } else {
            return null;
        }
    }

    //

    private void help(PrintStream helpStream) {
        try ( PrintWriter helpWriter = new PrintWriter(helpStream) ) {
            helpWriter.println();

            HelpFormatter helpFormatter = new HelpFormatter();
            boolean AUTO_USAGE = true;

            helpFormatter.printHelp(
                helpWriter,
                HelpFormatter.DEFAULT_WIDTH,
                JakartaTransformer.class.getName() + " [ options ]", // Command line syntax
                "\nOptions:", // Header
                getAppOptions(),
                HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD,
                "\n", // Footer
                !AUTO_USAGE);

            helpWriter.flush();
        }
    }

    //

    protected UTF8Properties loadProperties(AppOption ruleOption, String defaultReference) throws IOException, URISyntaxException {

    	URL rulesUrl;
        String rulesReference = getOptionValue(ruleOption);
        if ( rulesReference != null ) {
            info("Using external [ " + ruleOption + " ]: [ " + rulesReference + " ]\n");
            URI currentDirectoryUri = IO.work.toURI();
            rulesUrl = URIUtil.resolve(currentDirectoryUri, rulesReference).toURL();
            info("External [ " + ruleOption + " ] URL [ " + rulesUrl + " ]\n");
        } else {
            rulesReference = defaultReference;
            info("Using internal [ " + ruleOption + " ]: [ " + rulesReference + " ]\n");
            rulesUrl = getClass().getResource(rulesReference);
            if ( rulesUrl == null ) {
        		info("Default [ " + AppOption.RULES_SELECTIONS + " ] were not found [ " + rulesReference + " ]");
        		return null;
            } else {
                info("Default [ " + ruleOption + " ] URL [ " + rulesUrl + " ]\n");
            }
        }

        try ( InputStream rulesStream = rulesUrl.openStream() ) {
            UTF8Properties properties = new UTF8Properties();
            properties.load(rulesStream);
            return properties;
        }
    }

    private class TransformOptions {
    	public boolean isVerbose;
    	public boolean isTerse;

    	public Set<String> includes;
    	public Set<String> excludes;

    	public boolean invert;
    	public Map<String, String> packageRenames;
    	public Map<String, String> packageVersions;
    	public Map<String, BundleData> bundleUpdates;

    	public CompositeActionImpl rootAction;
    	public ActionImpl acceptedAction;

    	public String inputName;
        public String inputPath;
        public File inputFile;

        public boolean allowOverwrite;

        public String outputName;
        public String outputPath;
        public File outputFile;

    	protected void setLogging() {
            if ( hasOption(AppOption.TERSE) ) {
            	isTerse = true;
            	isVerbose = false;
                info("Terse output requested\n");
            } else if ( hasOption(AppOption.VERBOSE) ) {
            	isTerse = false;
            	isVerbose = true;
                info("Verbose output requested\n");
            } else {
            	isTerse = false;
            	isVerbose = false;
            }
    	}

    	private LoggerImpl logger;

    	protected LoggerImpl getLogger() {
    		if ( logger == null ) {
    			logger = new LoggerImpl( getInfoStream(), isTerse, isVerbose );
    		}
    		return logger;
    	}

    	private InputBufferImpl buffer;
    	
    	protected InputBufferImpl getBuffer() {
    		if ( buffer == null ) {
    			buffer = new InputBufferImpl();
    		}
    		return buffer;
    	}
    	
    	protected boolean setRules() throws IOException, URISyntaxException, IllegalArgumentException {
    		UTF8Properties selectionProperties = loadProperties(AppOption.RULES_SELECTIONS, DEFAULT_SELECTIONS_REFERENCE);
    		UTF8Properties renameProperties = loadProperties(AppOption.RULES_RENAMES, DEFAULT_RENAMES_REFERENCE);
    		UTF8Properties versionProperties = loadProperties(AppOption.RULES_VERSIONS, DEFAULT_VERSIONS_REFERENCE);
    		UTF8Properties updateProperties = loadProperties(AppOption.RULES_BUNDLES, DEFAULT_BUNDLES_REFERENCE);

        	invert = hasOption(AppOption.INVERT);

        	includes = new HashSet<String>();
        	excludes = new HashSet<String>();

        	if ( selectionProperties != null ) {
        		JakartaTransformProperties.setSelections(includes, excludes, selectionProperties);
        	} else {
        		info("All resources will be selected");
        	}

        	if ( renameProperties != null ) {
        		Map<String, String> renames = JakartaTransformProperties.getPackageRenames(renameProperties);
        		if ( invert ) {
        			renames = JakartaTransformProperties.invert(renames);
        		}
        		packageRenames = renames;
        	} else {
        		info("No package renames are available");
        		packageRenames = null;
        	}

        	if ( versionProperties != null ) {
        		packageVersions = JakartaTransformProperties.getPackageVersions(versionProperties);
        	} else {
        		info("Package versions will not be updated");
        	}

        	if ( updateProperties != null ) {
        		bundleUpdates = JakartaTransformProperties.getBundleUpdates(updateProperties);
        		// throws IllegalArgumentException
        	} else {
        		info("Bundle identities will not be updated");
        	}
        	
        	return ( packageRenames != null );
    	}

    	protected void logRules(PrintStream logStream) {
    		logStream.println("Includes:");
    		if ( includes.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( String include : includes ) {
    				logStream.println("  [ " + include + " ]");
    			}
    		}

      		logStream.println("Excludes:");
    		if ( excludes.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( String exclude : excludes ) {
    				logStream.println("  [ " + exclude + " ]");
    			}
    		}

    		if ( invert ) {
          		logStream.println("Package Renames: [ ** INVERTED ** ]");
    		} else {
          		logStream.println("Package Renames:");
    		}

    		if ( packageRenames.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( Map.Entry<String, String> renameEntry : packageRenames.entrySet() ) {
        			logStream.println("  [ " + renameEntry.getKey() + " ]: [ " + renameEntry.getValue() + " ]");
    			}
    		}

    		logStream.println("Package Versions:");
    		if ( packageVersions.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( Map.Entry<String, String> versionEntry : packageVersions.entrySet() ) {
        			logStream.println("  [ " + versionEntry.getKey() + " ]: [ " + versionEntry.getValue() + " ]");
    			}
    		}

    		logStream.println("Bundle Updates:");
    		if ( bundleUpdates.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( Map.Entry<String, BundleData> updateEntry : bundleUpdates.entrySet() ) {
    				BundleData updateData = updateEntry.getValue();

    				logStream.println("  [ " + updateEntry.getKey() + " ]: [ " + updateData.getSymbolicName() + " ]");

        			logStream.println("    [ Version ]: [ " + updateData.getVersion() + " ]");

        			if ( updateData.getAddName() ) {
        				logStream.println("    [ Name ]: [ " + BundleData.ADDITIVE_CHAR + updateData.getName() + " ]");
        			} else {
        				logStream.println("    [ Name ]: [ " + updateData.getName() + " ]");
        			}

        			if ( updateData.getAddDescription() ) {
        				logStream.println("    [ Description ]: [ " + BundleData.ADDITIVE_CHAR + updateData.getDescription() + " ]");
        			} else {
        				logStream.println("    [ Description ]: [ " + updateData.getDescription() + " ]");
        			}
    			}
    		}
    	}

    	private SelectionRuleImpl selectionRules;

    	protected SelectionRuleImpl getSelectionRule() {
    		if ( selectionRules == null ) {
    			selectionRules = new SelectionRuleImpl(
    				getLogger(),
    				includes, excludes);
    		}
    		return selectionRules;
    	}

    	private SignatureRuleImpl signatureRules;

    	protected SignatureRuleImpl getSignatureRule() {
    		if ( signatureRules == null ) {
    			signatureRules =  new SignatureRuleImpl(
        			getLogger(),
        			packageRenames, packageVersions, bundleUpdates);
    		}
    		return signatureRules;
    	}

        protected boolean setInput() {
        	inputName = getInputFileName();
            if ( inputName == null ) {
                error("No input file was specified\n");
                return false;
            }

            inputPath = inputFile.getAbsolutePath();
            info("      [ %s ]", inputPath);

            inputFile = new File(inputName);
            if ( !inputFile.exists() ) {
                error("Input does not exist [ " + inputPath + " ]\n");
                return false;
            }

            return true;
        }

        protected boolean setOutput() {
            outputName = getOutputFileName();
            outputFile = new File(outputName);
            outputPath = outputFile.getAbsolutePath();

            allowOverwrite = hasOption(AppOption.OVERWRITE);

            if ( outputFile.exists() ) {
            	if ( allowOverwrite ) {
                    info("Output exists and will be overwritten [ " + outputPath + " ]\n");
            	} else {
            		error("Output already exists [ " + outputPath + " ]\n");
            		return false;
            	}
            } else {
            	if ( allowOverwrite ) {
                    info("Overwritten specified, but output does not exist");
            	}
            }

            info("Output    [ %s ]\n", outputName);
            info("          [ %s ]\n", outputPath);
            return true;
        }

        public CompositeActionImpl getRootAction() {
        	if ( rootAction == null ) {
        		CompositeActionImpl useRootAction = new CompositeActionImpl(
                    getLogger(), getBuffer(), getSelectionRule(), getSignatureRule() );

        		// The root action knows about all action types, and, as a special case,
        		// knows about directory actions.

        		DirectoryActionImpl directoryAction =
        			useRootAction.addUsing( DirectoryActionImpl::new );
        		ClassActionImpl classAction =
        			useRootAction.addUsing( ClassActionImpl::new );
        		ServiceConfigActionImpl serviceConfigAction =
        			useRootAction.addUsing( ServiceConfigActionImpl::new );
        		ManifestActionImpl manifestAction =
        			useRootAction.addUsing( ManifestActionImpl::newManifestAction );
        		ManifestActionImpl featureAction =
        			useRootAction.addUsing( ManifestActionImpl::newFeatureAction );
        		JarActionImpl jarAction =
        			useRootAction.addUsing( JarActionImpl::new );
        		NullActionImpl nullAction =
        			useRootAction.addUsing( NullActionImpl::new );

        		// Directory actions know about all actions except for directory actions.

        		directoryAction.addAction(classAction);
        		directoryAction.addAction(serviceConfigAction);
        		directoryAction.addAction(manifestAction);
        		directoryAction.addAction(featureAction);
        		directoryAction.addAction(jarAction);
        		directoryAction.addAction(nullAction);

        		// Jar actions know about all actions except directory actions and jar actions.
        		// TODO: Should jars be allowed to nest other archives?
        		jarAction.addAction(classAction);
        		jarAction.addAction(serviceConfigAction);
        		jarAction.addAction(manifestAction);
        		jarAction.addAction(featureAction);
        		jarAction.addAction(nullAction);
        	}

        	return rootAction;
        }

        public boolean acceptAction() {
        	String actionName = getOptionValue(AppOption.ACTION);

        	if ( actionName != null ) {
        		for ( ActionImpl action : getRootAction().getActions() ) {
        			if ( action.getActionType().matches(actionName) ) {
        				info("Forced action [ %s ] [ %s ]\n", actionName, action.getName());
        				acceptedAction = action;
        				return true;
        			}
        		}
        		error("No match for forced action [ %s ]", actionName);
        		return false;

        	} else {
        		acceptedAction = getRootAction().acceptAction(inputName,  inputFile);
        		if ( acceptedAction == null ) {
        			error("No action selected for input [ %s ]", inputName);
        			return false;
        		} else {
        			info("Action selected for input [ %s ]: %s", inputName, acceptedAction.getName());
        			return true;
        		}
        	}
        }

        protected void transform()
        	throws JakartaTransformException {

        	acceptedAction.apply(inputName, inputFile, outputName, outputFile);

    		if ( acceptedAction.hasChanges() ) {
    			acceptedAction.getChanges().display( getInfoStream() );
    		}
        }
    }

    public int run() {
        try {
            setParsedArgs();
        } catch ( ParseException e ) {
            error("Exception parsing command line arguments: %s\n", e);
            help( getErrorStream() );
            return PARSE_ERROR_RC;
        }

        if ( hasOption(AppOption.HELP) || hasOption(AppOption.USAGE) ) {
            help( getErrorStream() );
            // TODO: Split help and usage
            return SUCCESS_RC; // TODO: Is this the correct return value?
        }

        TransformOptions options = new TransformOptions();

        options.setLogging();

        if ( !options.setInput() ) { 
            return TRANSFORM_ERROR_RC;
        }
         
        if ( !options.setOutput() ) {
            return TRANSFORM_ERROR_RC;
        }

        boolean loadedRules;
        try {
        	loadedRules = options.setRules();
        } catch ( Exception e ) {
            error("Exception loading rules: %s\n", e);
            return RULES_ERROR_RC;
        }
        if ( !loadedRules ) {
        	error("Transformation rules cannot be used");
        	return RULES_ERROR_RC;
        }
        if ( options.isVerbose ) {
        	options.logRules( getInfoStream() );
        }

        if ( !options.acceptAction() ) {
        	info("No action selected");
        	return ACTION_ERROR_RC;
        }

        try {
        	options.transform(); // throws JakartaTransformException
        } catch ( JakartaTransformException e ) {
            error("Transform failure: %s\n", e);
            return TRANSFORM_ERROR_RC;
        } catch ( Throwable th) {
        	error("Unexpected failure: %s\n", th);
            return TRANSFORM_ERROR_RC;
        }

        return SUCCESS_RC;
    }
}
