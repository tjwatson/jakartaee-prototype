package com.ibm.ws.jakarta.transformer;

import java.io.File;
import java.io.IOException;
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

import com.ibm.ws.jakarta.transformer.action.ActionType;
import com.ibm.ws.jakarta.transformer.action.BundleData;
import com.ibm.ws.jakarta.transformer.action.impl.ActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.ClassActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.CompositeActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.DirectoryActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.InputBufferImpl;
import com.ibm.ws.jakarta.transformer.action.impl.JarActionImpl;
import com.ibm.ws.jakarta.transformer.action.impl.JavaActionImpl;
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
    public static final int FILE_TYPE_ERROR_RC = 4;

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

    // public static final String DEFAULT_SELECTIONS_REFERENCE = "jakarta-selections.properties";
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
        RULES_DIRECT("td", "direct", "Transformation direct string replacements",
            OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

        INVERT("i", "invert", "Invert transformation rules",
           	!OptionSettings.HAS_ARG, !OptionSettings.IS_REQUIRED, OptionSettings.NO_GROUP),

        FILE_TYPE("t", "type", "Input file type",
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

    public void setArgs(String[] args) {
        this.args = args;
    }

    protected String[] getArgs() {
        return args;
    }

    public void setParsedArgs() throws ParseException {
        CommandLineParser parser = new DefaultParser();
        this.parsedArgs = parser.parse( getAppOptions(), getArgs());
    }

    protected CommandLine getParsedArgs() {
        return parsedArgs;
    }

    protected String getInputFileNameFromCommandLine() {
        String[] useArgs = parsedArgs.getArgs();
        if ( useArgs != null ) {
            if ( useArgs.length > 0 ) {
                return useArgs[0]; // First argument
            } 
        }
        return null;
    }

    protected String getOutputFileNameFromCommandLine() {
        String[] useArgs = parsedArgs.getArgs();
        if ( useArgs != null ) {
            if ( useArgs.length > 1 ) {
                return useArgs[1]; // Second argument
            } 
        }
        return null;
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
                HelpFormatter.DEFAULT_WIDTH + 5,
                JakartaTransformer.class.getName() + " input [ output ] [ options ]", // Command line syntax
                "Options:", // Header
                getAppOptions(),
                HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD,
                "\n", // Footer
                !AUTO_USAGE);

            helpWriter.println("Actions:");
            for ( ActionType actionType : ActionType.values() ) {
            	helpWriter.println("  [ " + actionType.name() + " ]");
            }

            helpWriter.flush();

        }
    }

    //

    protected UTF8Properties loadProperties(AppOption ruleOption) throws IOException, URISyntaxException {
        String rulesReference = getOptionValue(ruleOption);

        if ( rulesReference == null ) {
        	info("Skipping option [ %s ]\n", ruleOption);
        	return FileUtils.createProperties();
        } else {
        	return loadExternalProperties(ruleOption, rulesReference);
        }
    }

    protected UTF8Properties loadProperties(AppOption ruleOption, String defaultReference)
    	throws IOException, URISyntaxException {

        String rulesReference = getOptionValue(ruleOption);
        if ( rulesReference != null ) {
        	return loadExternalProperties(ruleOption, rulesReference);
        } else {
        	return loadDefaultProperties(ruleOption, defaultReference);
        }
    }

    protected UTF8Properties loadDefaultProperties(AppOption ruleOption, String defaultReference)
        	throws IOException {

            info("Using internal [ %s ]: [ %s ]\n", ruleOption, defaultReference);
            URL rulesUrl = getClass().getResource(defaultReference);
            if ( rulesUrl == null ) {
        		info("Default [ %s ] were not found [ %s ]\n", AppOption.RULES_SELECTIONS, defaultReference);
        		return null;
            } else {
                info("Default [ %s ] URL [ %s ]\n", ruleOption, rulesUrl);
            }
            return FileUtils.loadProperties(rulesUrl);
        }

        protected UTF8Properties loadExternalProperties(AppOption ruleOption, String externalReference)
        	throws URISyntaxException, IOException {

        	info("Using external [ %s ]: [ %s ]\n", ruleOption, externalReference);
        	URI currentDirectoryUri = IO.work.toURI();
        	URL rulesUrl = URIUtil.resolve(currentDirectoryUri, externalReference).toURL();
        	info("External [ %s ] URL [ %s ]\n", ruleOption, rulesUrl);

        	return FileUtils.loadProperties(rulesUrl);
        }

    //

    public TransformOptions getTransformOptions() {
        return new TransformOptions();
    }

    public class TransformOptions {
    	public boolean isVerbose;
    	public boolean isTerse;

    	public Set<String> includes;
    	public Set<String> excludes;

    	public boolean invert;
    	public Map<String, String> packageRenames;
    	public Map<String, String> packageVersions;
    	public Map<String, BundleData> bundleUpdates;
    	public Map<String, String> directStrings;

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
    	
    	public String getInputFileName() {
    	    return inputName;
    	}
    	
        public String getOutputFileName() {
            return outputName;
        }

    	private LoggerImpl logger;

    	public LoggerImpl getLogger() {
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

    	public boolean setRules() throws IOException, URISyntaxException, IllegalArgumentException {
    		UTF8Properties selectionProperties = loadProperties(AppOption.RULES_SELECTIONS);
    		UTF8Properties renameProperties = loadProperties(AppOption.RULES_RENAMES, DEFAULT_RENAMES_REFERENCE);
    		UTF8Properties versionProperties = loadProperties(AppOption.RULES_VERSIONS, DEFAULT_VERSIONS_REFERENCE);
    		UTF8Properties updateProperties = loadProperties(AppOption.RULES_BUNDLES, DEFAULT_BUNDLES_REFERENCE);
    		UTF8Properties directProperties = loadProperties(AppOption.RULES_DIRECT);

        	invert = hasOption(AppOption.INVERT);

        	includes = new HashSet<String>();
        	excludes = new HashSet<String>();

        	if ( selectionProperties != null ) {
        		JakartaTransformProperties.setSelections(includes, excludes, selectionProperties);
        	} else {
        		info("All resources will be selected\n");
        	}

        	if ( renameProperties != null ) {
        		Map<String, String> renames = JakartaTransformProperties.getPackageRenames(renameProperties);
        		if ( invert ) {
        			renames = JakartaTransformProperties.invert(renames);
        		}
        		packageRenames = renames;
        	} else {
        		info("No package renames are available\n");
        		packageRenames = null;
        	}

        	if ( versionProperties != null ) {
        		packageVersions = JakartaTransformProperties.getPackageVersions(versionProperties);
        	} else {
        		info("Package versions will not be updated\n");
        	}

        	if ( updateProperties != null ) {
        		bundleUpdates = JakartaTransformProperties.getBundleUpdates(updateProperties);
        		// throws IllegalArgumentException
        	} else {
        		info("Bundle identities will not be updated\n");
        	}

        	directStrings = JakartaTransformProperties.getDirectStrings(directProperties);

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

      		logStream.println("Direct strings:");
    		if ( directStrings.isEmpty() ) {
    			logStream.println("  [ ** NONE ** ]");
    		} else {
    			for ( Map.Entry<String, String> directEntry : directStrings.entrySet() ) {
    				logStream.println( "  [ " + directEntry.getKey() + " ]: [ " + directEntry.getValue() + "]");
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
        			packageRenames, packageVersions, bundleUpdates,
        			directStrings);
    		}
    		return signatureRules;
    	}

        public boolean setInput() {
        	String useInputName = getInputFileNameFromCommandLine();
            if ( useInputName == null ) {
                error("No input file was specified\n");
                return false;
            }

            inputName = FileUtils.normalize(useInputName);
			inputFile = new File(inputName);
            inputPath = inputFile.getAbsolutePath();

            if ( !inputFile.exists() ) {
                error("Input does not exist [ %s ] [ %s ]\n", inputName, inputPath);
                return false;
            }

            info("Input     [ %s ]\n", inputName);
            info("          [ %s ]\n", inputPath);
            return true;
        }

        public static final String OUTPUT_PREFIX = "output_";


//      info("Output file not specified.\n");
//
//      final String OUTPUT_PREFIX = "output_";
//      String inputFileName = getInputFileName();
//      int indexOfLastSlash = inputFileName.lastIndexOf('/');
//      if (indexOfLastSlash == -1 ) {
//          return OUTPUT_PREFIX + inputFileName; 
//      } else {
//          return inputFileName.substring(0, indexOfLastSlash+1) + OUTPUT_PREFIX + inputFileName.substring(indexOfLastSlash+1);
//      }

        public boolean setOutput() {
        	String useOutputName = getOutputFileNameFromCommandLine();

        	boolean isExplicit;

        	if ( isExplicit = (useOutputName != null) ) {
        		useOutputName = FileUtils.normalize(useOutputName);

        	} else {
        		int indexOfLastSlash = inputName.lastIndexOf('/');
        		if ( indexOfLastSlash == -1 ) {
        			useOutputName = OUTPUT_PREFIX + inputName;
        		} else {
        			String inputPrefix = inputName.substring( 0, indexOfLastSlash + 1 ); 
        			String inputSuffix = inputName.substring( indexOfLastSlash + 1 ); 
        			useOutputName = inputPrefix + OUTPUT_PREFIX + inputSuffix;
        		}
        	}

        	File useOutputFile = new File(useOutputName);
        	String useOutputPath = useOutputFile.getAbsolutePath();

        	boolean putIntoDirectory;

            if ( putIntoDirectory = (inputFile.isFile() && useOutputFile.isDirectory()) ) {
            	useOutputName = useOutputName + '/' + inputName;
            	if ( isVerbose ) {
            		info("Output generated using input name and output directory [ %s ]\n", useOutputName);
            	}

            	useOutputFile = new File(useOutputName);
            	useOutputPath = useOutputFile.getAbsolutePath();
            }

            String outputCase;
            if ( isExplicit ) {
            	if ( putIntoDirectory ) {
            		outputCase = "Explicit directory";
            	} else {
            		outputCase = "Explicit";
            	}
            } else {
            	if ( putIntoDirectory ) {
            		outputCase = "Directory generated from input";
            	} else {
            		outputCase = "Generated from input";
            	}
            }

            info("Output    [ %s ] (%s)\n", useOutputName, outputCase);
            info("          [ %s ]\n", useOutputPath);

            allowOverwrite = hasOption(AppOption.OVERWRITE);
            if ( allowOverwrite) {
            	info("Overwrite of output is enabled\n");
            }

            if ( useOutputFile.exists() ) {
            	if ( allowOverwrite ) {
                    info("Output exists and will be overwritten [ %s ]\n", useOutputPath);
            	} else {
            		error("Output already exists [ %s ]\n", useOutputPath);
            		return false;
            	}
            } else {
            	if ( allowOverwrite ) {
            		if ( isVerbose ) {
            			info("Overwritten specified, but output [ %s ] does not exist\n", useOutputPath);
            		}
            	}
            }

            outputName = useOutputName;
            outputFile = useOutputFile;
            outputPath = useOutputPath;

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
        		JavaActionImpl javaAction =
        			useRootAction.addUsing( JavaActionImpl::new );
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
        		directoryAction.addAction(javaAction);
        		directoryAction.addAction(serviceConfigAction);
        		directoryAction.addAction(manifestAction);
        		directoryAction.addAction(featureAction);
        		directoryAction.addAction(jarAction);
        		directoryAction.addAction(nullAction);

        		// Jar actions know about all actions except directory actions and jar actions.
        		// TODO: Should jars be allowed to nest other archives?

        		jarAction.addAction(classAction);
        		jarAction.addAction(javaAction);
        		jarAction.addAction(serviceConfigAction);
        		jarAction.addAction(manifestAction);
        		jarAction.addAction(featureAction);
        		jarAction.addAction(nullAction);

        		rootAction = useRootAction;
            }

            return rootAction;
        }

        public boolean acceptAction() {
        	String actionName = getOptionValue(AppOption.FILE_TYPE);
        	if ( actionName != null ) {
        		for ( ActionImpl action : getRootAction().getActions() ) {
        			if ( action.getActionType().matches(actionName) ) {
        				info("Forced action [ %s ] [ %s ]\n", actionName, action.getName());
        				acceptedAction = action;
        				return true;
        			}
        		}
        		error("No match for forced action [ %s ]\n", actionName);
        		return false;

        	} else {
        		acceptedAction = getRootAction().acceptAction(inputName,  inputFile);
        		if ( acceptedAction == null ) {
        			error("No action selected for input [ %s ]\n", inputName);
        			return false;
        		} else {
        			info("Action selected for input [ %s ]: %s\n", inputName, acceptedAction.getName());
        			return true;
        		}
        	}
        }

        public void transform()
        	throws JakartaTransformException {

        	acceptedAction.apply(inputName, inputFile, outputFile);

    		if ( acceptedAction.hasChanges() ) {
    			acceptedAction.getChanges().displayChanges( getInfoStream(), inputPath, outputPath );
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
        	return FILE_TYPE_ERROR_RC;
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
