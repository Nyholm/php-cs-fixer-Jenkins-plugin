package com.happyr.jenkins.phpcsfixer;

import com.happyr.jenkins.phpcsfixer.console.ConsoleAnnotator;
import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.ByteArrayOutputStream2;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


/**
 * Sample {@link Builder}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link PHPCsFixerBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class PHPCsFixerBuilder extends Builder {

    /**
     * The parameters for this project
     */
    private final String projectParameters;

    /**
     * The working dir
     */
    private FilePath workingDir;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PHPCsFixerBuilder(String projectParameters) {
        this.projectParameters = projectParameters;
    }

    public String getProjectParameters() {
        if (projectParameters == null)
            return new String();
        return projectParameters;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // This is where you 'build' the project.
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = build.getEnvironment(listener);
        workingDir = build.getModuleRoot();

        //prepare the arguments for running the command
        prepareCommand(args, launcher, env);
        listener.getLogger().println("Starting to run php-cs-fixer");
        final long startTime = System.currentTimeMillis();
        try {
            ConsoleAnnotator console = new ConsoleAnnotator(listener.getLogger(), build.getCharset());
            int result;
            try {
                ArrayList<String> files = getListOfPHPFiles(launcher, env);
                ArgumentListBuilder singleFileArgs;

                // for each changed file
                for (String file : files) {

                    singleFileArgs = args.clone();
                    singleFileArgs.add(file);

                    result = runCommand(singleFileArgs, launcher, env, console);

                    if (result != 0) {
                        listener.finished(Result.ABORTED);
                        return false;
                    }
                }

            } finally {
                final long processingTime = System.currentTimeMillis() - startTime;
                listener.getLogger().println(String.format("Finished php-cs-fixer in %.2f seconds", (double) processingTime / 1000));

                console.forceEol();
            }

            return true;
        } catch (final IOException e) {
            Util.displayIOException(e, listener);
            final long processingTime = System.currentTimeMillis() - startTime;
            final String errorMessage = "Failed... somehow...";
            e.printStackTrace(listener.fatalError(errorMessage));
            return false;
        }
    }

    /**
     * Prepare the command. Download php-cs-fixer if we need to.
     * <p/>
     * Also add the user parameters
     *
     * @param args
     * @param launcher
     * @param env
     * @throws InterruptedException
     * @throws IOException
     */
    private void prepareCommand(ArgumentListBuilder args, Launcher launcher, EnvVars env) throws InterruptedException, IOException {
        ByteArrayOutputStream2 output = new ByteArrayOutputStream2();
        String scriptPath = getDescriptor().getFixerPath();
        if (scriptPath.isEmpty()) {
            //download
            runCommand("wget http://get.sensiolabs.org/php-cs-fixer.phar -O php-cs-fixer", launcher, env, output);
            args.addTokenized("php");
            args.addTokenized("php-cs-fixer");

        } else {
            args.add(scriptPath);
        }

        if (getProjectParameters().isEmpty()) {
            // If we don't have any extra parameters to this project, use global
            args.addTokenized(getDescriptor().getParameters());
        } else {
            args.addTokenized(projectParameters);
        }
    }

    /**
     * Get a list of the file changed since last commit or since last successful build
     *
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private ArrayList<String> getListOfPHPFiles(Launcher launcher, EnvVars env) throws InterruptedException, IOException {
        ArrayList<String> phpFiles = new ArrayList<String>();
        ByteArrayOutputStream2 output = new ByteArrayOutputStream2();
        ArgumentListBuilder args = new ArgumentListBuilder();

        //prepare the argument
        args
                .add("git")
                .add("diff")
                        // make sure git does not cut the path to the file (=1000)
                .add("--stat=1000");

        String lastSuccessfulCommit = env.get("GIT_PREVIOUS_SUCCESSFUL_COMMIT");
        if (lastSuccessfulCommit != null) {
            if (lastSuccessfulCommit.equals(env.get("GIT_COMMIT"))) {
                //if nothing updated.. everything is fine
                return phpFiles;
            }

            args.add(lastSuccessfulCommit);
        } else {
            args.add(env.get("GIT_PREVIOUS_COMMIT"));
        }
        args.add(env.get("GIT_COMMIT"));

        runCommand(args, launcher, env, output);
        String lines[] = output.toString().split("\\r?\\n");

        // everything but last line
        for (int i = 0; i < lines.length - 1; i++) {
            String file = lines[i].replaceAll("\\|.*$", "").trim();
            //Only check php files
            if (!file.matches(".+\\.php$"))
                continue;

            //check if file exists
            if (!(new File(env.get("WORKSPACE") + "/" + file).exists())) {
                continue;
            }

            phpFiles.add(file);
        }

        return phpFiles;
    }

    /**
     * @param cmd
     * @param launcher
     * @param environment
     * @param output
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private int runCommand(String cmd, Launcher launcher, EnvVars environment, OutputStream output)
            throws InterruptedException, IOException {
        return launcher.launch().cmds(new ArgumentListBuilder().addTokenized(cmd)).envs(environment).stdout(output).pwd(workingDir).join();
    }

    /**
     * @param argumentList
     * @param launcher
     * @param environment
     * @param output
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private int runCommand(ArgumentListBuilder argumentList, Launcher launcher, EnvVars environment, OutputStream output)
            throws InterruptedException, IOException {
        return launcher.launch().cmds(argumentList).envs(environment).stdout(output).pwd(workingDir).join();
    }


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link PHPCsFixerBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resources/hudson/plugins/hello_world/PHPCsFixerBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         * <p/>
         * <p/>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String fixerPath;
        private String parameters = "fix --level=psr2 --dry-run --diff";

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         * <p/>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {

            return FormValidation.ok();
        }

        /**
         * Indicates that this builder can be used with all kinds of project types
         *
         * @param aClass
         * @return booleans
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {

            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "PHP Coding Standards Fixer";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            fixerPath = formData.getString("fixerPath");
            parameters = formData.getString("parameters");

            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }


        public String getParameters() {
            if (parameters == null)
                return new String();

            return parameters;
        }

        public String getFixerPath() {
            if (fixerPath == null)
                return new String();
            return fixerPath;
        }
    }
}

