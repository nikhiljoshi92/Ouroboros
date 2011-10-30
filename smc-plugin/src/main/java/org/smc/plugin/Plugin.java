package org.smc.plugin;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import net.sf.smc.Smc;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal generate
 * 
 * @phase generate-sources
 */
public class Plugin extends AbstractMojo {
    /**
     * Location of the project home directory.
     * 
     * @parameter expression="${project.home.directory}"
     * @required
     */
    private File    projectDirectory;

    /**
     * Location of the build directory.
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File    buildDirectory;

    /**
     * DebugLevel. 0, 1: Adds debug output messages to the generated code. 0
     * produces output messages which signal when the FSM has exited a state,
     * entered a state, entered and exited a transition. 1 includes the 0 output
     * and addition output for entering and exiting state Exit and Entry
     * actions.
     * 
     * @parameter
     */
    private int     debugLevel      = -1;

    /**
     * Generic collections. May be used only with target languages csharp, java
     * or vb and reflection. Causes SMC to use generic collections for
     * reflection.
     * 
     * @parameter
     */
    private boolean generic         = true;

    /**
     * Graph level. Specifies how much detail to place into the DOT file. Level
     * 0 is the least detail and 2 is the greatest.
     * 
     * @parameter
     */
    private int     graphLevel      = -1;

    /**
     * Reflection. May be used only with target languages csharp, groovy, java,
     * lua, perl, php, python, ruby, scala, tcl and vb. Causes SMC to generate a
     * getTransitions method, allowing applications to query the available
     * transitions for the current state.
     * 
     * @parameter
     */
    private boolean reflection      = false;

    /**
     * Serialization. Generate unique integer IDs for each state. These IDs can
     * be used when persisting an FSM.
     * 
     * @parameter
     */
    private boolean serial          = true;

    /**
     * State machine files source directory, relative to the project root.
     * 
     * @parameter
     */
    private String  smDirectory     = "sm";

    /**
     * May be used only with the java, groovy, scala, vb and csharp target
     * languages. Causes SMC to:
     * <ul>
     * <li>Java: add the synchronized keyword to the transition method
     * declarations.</li>
     * <li>Groovy: add the synchronized keyword to the transition method
     * declarations.</li>
     * <li>Scala: add the synchronized keyword to the transition method
     * declarations.</li>
     * <li>VB.net: encapsulate the transition method's body in a SyncLock Me,
     * End SyncLock block.</li>
     * <li>C#: encapsulate the transition method's body in a lock(this) {...}
     * block.</li>
     * </ul>
     * 
     * @parameter
     */
    private boolean sync            = false;

    /**
     * Target language
     * 
     * @parameter
     */
    private String  target          = "java";

    /**
     * Generated source directory, relative to the project build directory
     * 
     * @parameter
     */
    private String  targetDirectory = "generated-sources/sm";

    /**
     * Verbose output.
     * 
     * @parameter
     */
    private boolean verbose         = false;

    @Override
    public void execute() throws MojoExecutionException {
        ArrayList<String> mainArgs = new ArrayList<String>();
        mainArgs.add("-" + target);
        if (verbose) {
            mainArgs.add("-verbose");
        }
        if (sync) {
            mainArgs.add("-sync");
        }
        switch (debugLevel) {
            case 0:
                mainArgs.add("-g0");
                break;
            case 1:
                mainArgs.add("-g1");
                break;
            default:
        }
        if (generic) {
            mainArgs.add("-generic");
        }
        if (reflection) {
            mainArgs.add("-reflection");
        }
        if (serial) {
            mainArgs.add("-serial");
        }
        switch (graphLevel) {
            case 0:
                mainArgs.add("-gLevel");
                mainArgs.add("0");
                break;
            case 1:
                mainArgs.add("-gLevel");
                mainArgs.add("1");
                break;
            default:
        }

        mainArgs.add("-d");
        mainArgs.add(new File(buildDirectory, targetDirectory).getAbsolutePath());

        for (File source : new File(projectDirectory, smDirectory).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".sm");
            }
        })) {
            ArrayList<String> args = new ArrayList<String>(mainArgs);
            args.add(source.getAbsolutePath());
            Smc.main(mainArgs.toArray(new String[0]));
        }
    }
}
