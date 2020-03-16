// Patched by John Cowan to add -H option to invoke TagSoup parser
package com.icl.saxon;
import com.icl.saxon.tree.TreeBuilder;
import com.icl.saxon.tinytree.TinyBuilder;
import com.icl.saxon.om.Builder;
import com.icl.saxon.om.Navigator;
import com.icl.saxon.om.DocumentInfo;
import com.icl.saxon.om.Namespace;
import com.icl.saxon.om.NamePool;
import com.icl.saxon.expr.*;
import com.icl.saxon.style.*;
import com.icl.saxon.output.*;
import com.icl.saxon.trace.*;
import com.icl.saxon.style.TerminationException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.*;

import org.w3c.dom.Node;
import org.w3c.dom.Document;

import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;

import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.stream.*;

/**
  * This <B>StyleSheet</B> class is the entry point to the Saxon XSLT Processor. This
  * class is provided to control the processor from the command line.<p>
  *
  * The XSLT syntax supported conforms to the W3C XSLT 1.0 and XPath 1.0 recommendation.
  * Only the transformation language is implemented (not the formatting objects).
  * Saxon extensions are documented in the file extensions.html
  *
  * @author M.H.Kay (mhkay@iclway.co.uk)
  * @author John Cowan (cowan@ccil.org)
  */
  
public class StyleSheet  {

	protected TransformerFactoryImpl factory = new TransformerFactoryImpl();
	
    protected NamePool namePool = NamePool.getDefaultNamePool();
    boolean showTime = false;
    int repeat = 1;
        
    /**
    * Main program, can be used directly from the command line.
    * <p>The format is:</P>
    * <p>java com.icl.saxon.StyleSheet [options] <I>source-file</I> <I>style-file</I> &gt;<I>output-file</I></P>
    * <p>followed by any number of parameters in the form {keyword=value}... which can be
    * referenced from within the stylesheet.</p>
    * <p>This program applies the XSL style sheet in style-file to the source XML document in source-file.</p>
    */
    
    public static void main (String args[])
        throws java.lang.Exception
    {
        // the real work is delegated to another routine so that it can be used in a subclass
        (new StyleSheet()).doMain(args, new StyleSheet(), " java com.icl.saxon.StyleSheet");
    }

    /**
    * Support method for main program. This support method can also be invoked from subclasses
    * that support the same command line interface
    * @param args the command-line arguments
    * @param app instance of the StyleSheet class (or a subclass) to be invoked
    * @param name name of the class, to be used in error messages
    */

    protected void doMain(String args[], StyleSheet app, String name) {
        
        
        String sourceFileName = null;
        String styleFileName = null;
        File sourceFile = null;
        File styleFile = null;
        File outputFile = null;
        boolean useURLs = false;
        ParameterSet params = new ParameterSet();
        Properties outputProperties = new Properties();
        String outputFileName = null;
        boolean useAssociatedStylesheet = false;
        boolean wholeDirectory = false;
        
				// Check the command-line arguments.

        try {
            int i = 0;
            while (true) {
                if (i>=args.length) badUsage(name, "No source file name");

                if (args[i].charAt(0)=='-') {

                    if (args[i].equals("-a")) {
                        useAssociatedStylesheet = true;
                        i++;
                    }

                    else if (args[i].equals("-ds")) {
                        factory.setAttribute(
                            FeatureKeys.TREE_MODEL,
                            new Integer(Builder.STANDARD_TREE));
                        i++;
                    }

                    else if (args[i].equals("-dt")) {
                        factory.setAttribute(
                            FeatureKeys.TREE_MODEL,
                            new Integer(Builder.TINY_TREE));
                        i++;
                    }


                    else if (args[i].equals("-l")) {
                        factory.setAttribute(
                            FeatureKeys.LINE_NUMBERING,
                            new Boolean(true));
                        i++;
                    }

                    else if (args[i].equals("-u")) {
                        useURLs = true;
                        i++;
                    }
        
                    else if (args[i].equals("-t")) {
                        System.err.println(Version.getProductName());
                        System.err.println("Java version " + System.getProperty("java.version"));
                        factory.setAttribute(
                            FeatureKeys.TIMING,
                            new Boolean(true));

                        Loader.setTracing(true);
                        showTime = true;
                        i++;
                    }

                    else if (args[i].equals("-3")) {    // undocumented option: do it thrice
                        i++;
                        repeat = 3;
                    }

                    else if (args[i].equals("-9")) {    // undocumented option: do it nine times
                        i++;
                        repeat = 9;
                    }
        
                    else if (args[i].equals("-o")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No output file name");
                        outputFileName = args[i++];
                    }

                    else if (args[i].equals("-x")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No source parser class");
                        String sourceParserName = args[i++];
                        factory.setAttribute(
                                FeatureKeys.SOURCE_PARSER_CLASS,
                                sourceParserName);
                    }

                    else if (args[i].equals("-H")) {
                        i++;
                        String sourceParserName = "org.ccil.cowan.tagsoup.Parser";
                        factory.setAttribute(
                                FeatureKeys.SOURCE_PARSER_CLASS,
                                sourceParserName);
                    }

                    else if (args[i].equals("-y")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No style parser class");
                        String styleParserName = args[i++];
                        factory.setAttribute(
                                FeatureKeys.STYLE_PARSER_CLASS,
                                styleParserName);
                    }

                    else if (args[i].equals("-r")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No URIResolver class");
                        String r = args[i++];
                        factory.setURIResolver(makeURIResolver(r));
                    }

                    else if (args[i].equals("-T")) {
                        i++;
                        TraceListener traceListener = new com.icl.saxon.trace.SimpleTraceListener();
                        factory.setAttribute(
                                FeatureKeys.TRACE_LISTENER,
                                traceListener);
                        factory.setAttribute(
                                FeatureKeys.LINE_NUMBERING,
                                Boolean.TRUE);
                    }

                    else if (args[i].equals("-TL")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No TraceListener class");
                        TraceListener traceListener = makeTraceListener(args[i++]);
                        factory.setAttribute(
                                FeatureKeys.TRACE_LISTENER,
                                traceListener);                        
                        factory.setAttribute(
                                FeatureKeys.LINE_NUMBERING,
                                Boolean.TRUE);
                    }

                    else if (args[i].equals("-w0")) {
                        i++;
                        factory.setAttribute(
                                FeatureKeys.RECOVERY_POLICY,
                                new Integer(Controller.RECOVER_SILENTLY));              
                    }
                    else if (args[i].equals("-w1")) {
                        i++;
                        factory.setAttribute(
                                FeatureKeys.RECOVERY_POLICY,
                                new Integer(Controller.RECOVER_WITH_WARNINGS));      
                    }
                    else if (args[i].equals("-w2")) {
                        i++;
                        factory.setAttribute(
                                FeatureKeys.RECOVERY_POLICY,
                                new Integer(Controller.DO_NOT_RECOVER));      
                    }
                    
                    else if (args[i].equals("-m")) {
                        i++;
                        if (args.length < i+2) badUsage(name, "No message Emitter class");
                        factory.setAttribute(
                                FeatureKeys.MESSAGE_EMITTER_CLASS,
                                args[i++]);                              
                    }

                    else if (args[i].equals("-noext")) {
                        i++;
                        factory.setAttribute(
                                FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS,
                                new Boolean(false));                              
                    }

                    else badUsage(name, "Unknown option " + args[i]);
                }
            
                else break;
            }

            if (args.length < i+1 ) badUsage(name, "No source file name");        
            sourceFileName = args[i++];

            if (!useAssociatedStylesheet) {
                if (args.length < i+1 ) badUsage(name, "No stylesheet file name");        
                styleFileName = args[i++];
            }
        
            for (int p=i; p<args.length; p++) {
                String arg = args[p];
                int eq = arg.indexOf("=");
                if (eq<1 || eq>=arg.length()-1) badUsage(name, "Bad param=value pair on command line");
				String argname = arg.substring(0,eq);
				int argcode = namePool.allocate("", "", argname);
                params.put(argcode, new StringValue(arg.substring(eq+1)));
            }

            Source sourceInput = null;

            if (useURLs || sourceFileName.startsWith("http:") || sourceFileName.startsWith("file:")) {
                sourceInput = factory.getURIResolver().resolve(sourceFileName, null);

            } else {
                sourceFile = new File(sourceFileName);
                if (!sourceFile.exists()) {
                    quit("Source file " + sourceFile + " does not exist", 2);
                }
                if (sourceFile.isDirectory()) {
                    wholeDirectory = true;
                    if (outputFileName==null) {
                        quit("To process a directory, -o must be specified", 2);
                    } else if (outputFileName.equals(sourceFileName)) {
                        quit("Output directory must be different from input", 2);
                    } else {
                        outputFile = new File(outputFileName);
                        if (!outputFile.isDirectory()) {
                            quit("Input is a directory, but output is not", 2);
                        }
                    }
                } else {
                    ExtendedInputSource eis = new ExtendedInputSource(sourceFile);
                    sourceInput = new SAXSource(factory.getSourceParser(), eis);
                    eis.setEstimatedLength((int)sourceFile.length());
                }
            }

            if (outputFileName!=null && !wholeDirectory) {
                 outputFile = new File(outputFileName);
                 if (outputFile.isDirectory()) {
                            quit("Output is a directory, but input is not", 2);
                 }
            }

            if (useAssociatedStylesheet) {
                if (wholeDirectory) {
                    processDirectoryAssoc(sourceFile, outputFile, params);
                } else {
                    processFileAssoc(sourceInput, null, outputFile, params);
                }
            } else {
                
                long startTime = (new Date()).getTime();

                Source styleSource;
                if (useURLs || styleFileName.startsWith("http:")
                                 || styleFileName.startsWith("file:")) {
                    styleSource = factory.getURIResolver().resolve(styleFileName, null);
                    
                } else {
                    File sheetFile = new File(styleFileName);
                    if (!sheetFile.exists()) {
                        quit("Stylesheet file " + sheetFile + " does not exist", 2);
                    }
                    ExtendedInputSource eis = new ExtendedInputSource(sheetFile);
                    styleSource = new SAXSource(factory.getStyleParser(), eis);
                }
                    
                if (styleSource==null) {
                    quit("URIResolver for stylesheet file must return a Source", 2);
                }

                Templates sheet = factory.newTemplates(styleSource);

                if (showTime) {
                    long endTime = (new Date()).getTime();
                    System.err.println("Preparation time: " + (endTime-startTime) + " milliseconds");
                    startTime = endTime;
                }
                
                if (wholeDirectory) {        
                    processDirectory(sourceFile, sheet, outputFile, params);
                } else {
                    processFile(sourceInput, sheet, outputFile, params);
                }
            }
        } catch (TerminationException err) {
            quit(err.getMessage(), 1);            
        } catch (TransformerException err) {
            quit("Transformation failed: " + err.getMessage(), 2);
        } catch (Exception err2) {
            err2.printStackTrace();
        }
        

        //System.exit(0);
    }

    /**
    * Exit with a message
    */

    protected static void quit(String message, int code) {
        System.err.println(message);
        System.exit(code);
    }

    /**
    * Process each file in the source directory using its own associated stylesheet
    */

    public void processDirectoryAssoc(
        File sourceDir, File outputDir, ParameterSet params)
        throws Exception {

        String[] files = sourceDir.list();
        int failures = 0;
        for (int f=0; f<files.length; f++) {
            File file = new File(sourceDir, files[f]);
            if (!file.isDirectory()) {
                String localName = file.getName();
                try {
                    ExtendedInputSource eis = new ExtendedInputSource(file);
                    SAXSource source = new SAXSource(factory.getSourceParser(), eis);
                    processFileAssoc(source, localName, outputDir, params);
                } catch (TransformerException err) {
                    failures++;
                    System.err.println("While processing " + localName +
                         ": " + err.getMessage() + "\n");
                }
            }
        }
        if (failures>0) {
            throw new TransformerException(failures + " transformation" +
                 (failures==1?"":"s") + " failed");
        }
    }

    /**
    * Make an output file in the output directory, with filename extension derived from the
    * media-type produced by the stylesheet
    */

    private File makeOutputFile(File directory, String localName,
                                 Templates sheet) {
        String mediaType = sheet.getOutputProperties().getProperty(
                                    OutputKeys.MEDIA_TYPE);
        String suffix = ".xml";
        if ("text/html".equals(mediaType)) {
            suffix = ".html";
        } else if ("text/plain".equals(mediaType)) {
            suffix = ".txt";
        }
        String prefix = localName;
        if (localName.endsWith(".xml") || localName.endsWith(".XML")) {
            prefix = localName.substring(0, localName.length()-4);
        }
        return new File(directory, prefix+suffix);
    }
            

    /**
    * Process a single source file using its associated stylesheet(s)
    */

    public void processFileAssoc(
        Source sourceInput, String localName, File outputFile, ParameterSet params)
        throws TransformerException
    {
        if (showTime) {
            System.err.println("Processing " + sourceInput.getSystemId() + " using associated stylesheet");
        }
        long startTime = (new Date()).getTime();

        Source style = factory.getAssociatedStylesheet(sourceInput, null, null, null);
		Templates sheet = factory.newTemplates(style);
        if (showTime) {
            System.err.println("Prepared associated stylesheet " + style.getSystemId());
        }
		Transformer instance = sheet.newTransformer();			
        ((Controller)instance).setParams(params);
        
        File outFile = outputFile;
    
        if (outFile!=null && outFile.isDirectory()) {
            outFile = makeOutputFile(outFile, localName, sheet);
        }

        StreamResult result = 
            (outFile==null ? new StreamResult(System.out) : new StreamResult(outFile));
        
        try {
            instance.transform(sourceInput, result);
        } catch (TerminationException err) {
            throw err;
        } catch (TransformerException err) {
            // The error message will already have been displayed; don't do it twice
            throw new TransformerException("Run-time errors were reported");
        }
    
        if (showTime) {
            long endTime = (new Date()).getTime();
            System.err.println("Execution time: " + (endTime-startTime) + " milliseconds");
            startTime = endTime;
        }
    }

    /**
    * Process each file in the source directory using the same supplied stylesheet
    */

    public void processDirectory(
        File sourceDir, Templates sheet, File outputDir, ParameterSet params)
        throws TransformerException
    {
            
        String[] files = sourceDir.list();
        int failures = 0;
        for (int f=0; f<files.length; f++) {
            File file = new File(sourceDir, files[f]);
            String localName = file.getName();
            try {                
                if (!file.isDirectory()) {
                    File outputFile = makeOutputFile(outputDir, localName, sheet);
                    ExtendedInputSource eis = new ExtendedInputSource(file);
                    Source source = new SAXSource(factory.getSourceParser(), eis);
                    processFile(source, sheet, outputFile, params);
                }
            } catch (TransformerException err) {
                failures++;
                System.err.println("While processing " + localName + ": " + err.getMessage() + "\n");
            }                
        }
        if (failures>0) {
            throw new TransformerException(failures + " transformation" +
                 (failures==1?"":"s") + " failed");
        }
    }

    /**
    * Process a single file using a supplied stylesheet
    */

    public void processFile(
        Source source, Templates sheet, File outputFile, ParameterSet params)
        throws TransformerException {

        for (int r=0; r<repeat; r++) {      // repeat is for internal testing/timing
            if (showTime) {
                System.err.println("Processing " + source.getSystemId());                
            }
            long startTime = (new Date()).getTime();
            Transformer instance = sheet.newTransformer();
            ((Controller)instance).setParams(params);

            Result result =
                (outputFile==null ?
                    new StreamResult(System.out) :
                    new StreamResult(outputFile));

            try {
                instance.transform(source, result);
            } catch (TerminationException err) {
                throw err;
            } catch (TransformerException err) {
                // The message will already have been displayed; don't do it twice
                throw new TransformerException("Run-time errors were reported");
            }

            if (showTime) {
                long endTime = (new Date()).getTime();
                System.err.println("Execution time: " + (endTime-startTime) + " milliseconds");
                startTime = endTime;
            }
        }
    }

    protected void badUsage(String name, String message) {
        System.err.println(message);
        System.err.println(Version.getProductName());
        System.err.println("Usage: " + name + " [options] source-doc style-doc {param=value}...");
        System.err.println("Options: ");
        System.err.println("  -a              Use xml-stylesheet PI, not style-doc argument ");
        System.err.println("  -ds             Use standard tree data structure ");
        System.err.println("  -dt             Use tinytree data structure (default)");
        System.err.println("  -o filename     Send output to named file or directory ");
        System.err.println("  -m classname    Use specified Emitter class for xsl:message output ");
        System.err.println("  -r classname    Use specified URIResolver class ");
        System.err.println("  -t              Display version and timing information ");        
        System.err.println("  -T              Set standard TraceListener");  
        System.err.println("  -TL classname   Set a specific TraceListener");  
        System.err.println("  -u              Names are URLs not filenames ");
        System.err.println("  -w0             Recover silently from recoverable errors ");
        System.err.println("  -w1             Report recoverable errors and continue (default)");
        System.err.println("  -w2             Treat recoverable errors as fatal");
        System.err.println("  -x classname    Use specified SAX parser for source file ");
        System.err.println("  -H              Use TagSoup HTML parser for source file ");
        System.err.println("  -y classname    Use specified SAX parser for stylesheet ");
        System.err.println("  -?              Display this message ");
        System.exit(2);
    }

    public static URIResolver makeURIResolver (String className) 
    throws TransformerException
    {
        Object obj = Loader.getInstance(className);
        if (obj instanceof URIResolver) {
            return (URIResolver)obj;
        }
        throw new TransformerException("Class " + className + " is not a URIResolver");
    }

    public static TraceListener makeTraceListener (String className) 
    throws TransformerException
    {
        Object obj = Loader.getInstance(className);
        if (obj instanceof TraceListener) {
            return (TraceListener)obj;
        }        
        throw new TransformerException("Class " + className + " is not a TraceListener");
    }

}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/ 
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License. 
//
// The Original Code is: all this file. 
//
// The Initial Developer of the Original Code is
// Michael Kay of International Computers Limited (mhkay@iclway.co.uk).
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved. 
//
// Contributor(s): none. 
//
