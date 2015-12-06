package hudson.plugins.humbug;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HumbugNotifier extends Notifier {

    private Humbug humbug;
    private String stream;
    private String title;
    private String message;
    private String hudsonUrl;
    private boolean smartNotify;

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final Logger LOGGER = Logger.getLogger(HumbugNotifier.class.getName());

    public HumbugNotifier() {
        super();
        initialize();
    }

    @DataBoundConstructor
    public HumbugNotifier(String humbugStreamForProject, String humbugTitleForProject, String humbugMessageForProject) {
        super();
        initialize(humbugStreamForProject, humbugTitleForProject, humbugMessageForProject);
    }

    public HumbugNotifier(String url, String email, String apiKey, String stream, String hudsonUrl, boolean smartNotify) {
        super();
        initialize(url, email, apiKey, stream, "", "", hudsonUrl, smartNotify);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private void publish(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        // We call this every time in case our settings have changed
        // between the last time this was run and now.
        initialize();
        Result result = build.getResult();
        String changeString = "";
        try {
            if (!build.hasChangeSetComputed()) {
                changeString = "Could not determine changes since last build.";
            } else if (build.getChangeSet().iterator().hasNext()) {
                if (!build.getChangeSet().isEmptySet()) {
                    // If there seems to be a commit message at all, try to list all the changes.
                    changeString = "Changes since last build:\n";
                    for (ChangeLogSet.Entry e: build.getChangeSet()) {
                        String commitMsg = e.getMsg().trim();
                        if (commitMsg.length() > 47) {
                            commitMsg = commitMsg.substring(0, 46)  + "...";
                        }
                        String author = e.getAuthor().getDisplayName();
                        changeString += "\n* `"+ author + "` " + commitMsg;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                      "Exception while computing changes since last build:\n"
                       + ExceptionUtils.getStackTrace(e));
            changeString += "\nError determining changes since last build - please contact support@zulip.com.";
        }
        String resultString = result.toString();
        String message = "Build " + build.getDisplayName();

        if (hudsonUrl != null && hudsonUrl.length() > 1) {
            message = "[" + message + "](" + hudsonUrl + build.getUrl() + ")";
        }
        message += ": ";
        if (!smartNotify && result == Result.SUCCESS) {
            // SmartNotify is off, so a success is actually the common
            // case here; so don't yell about it.
            message += StringUtils.capitalize(resultString.toLowerCase());
        } else {
            message += "**" + resultString + "**";
            if (result == Result.SUCCESS) {
                message += " :white_check_mark:";
            } else {
                message += " :x:";
            }
        }
        if (changeString.length() > 0 ) {
            message += "\n\n";
            message += changeString;
        }

        final EnvVars env = build.getEnvironment(listener);

        if(this.message != "") {
            message += "\n\n";
            message += env.expand(this.message);
        }

        if(title == "") {
            title = build.getProject().getName();
        }

        final String expandedStream = env.expand(stream);
        final String expandedTitle = env.expand(title);

        humbug.sendStreamMessage(expandedStream, expandedTitle, message);
    }

    private void initialize()  {
	String s = ("" == DESCRIPTOR.getStreamForProject())? DESCRIPTOR.getStream() : DESCRIPTOR.getStreamForProject();
	String t = ("" == DESCRIPTOR.getTitleForProject())? "" : DESCRIPTOR.getTitleForProject();

        initialize(DESCRIPTOR.getUrl(), DESCRIPTOR.getEmail(), DESCRIPTOR.getApiKey(), s, t, "", DESCRIPTOR.getHudsonUrl(), DESCRIPTOR.getSmartNotify());
    }

    private void initialize(String streamName, String title, String message)  {
        if(streamName == ""){
            streamName = DESCRIPTOR.getStream();
        }

        initialize(DESCRIPTOR.getUrl(), DESCRIPTOR.getEmail(), DESCRIPTOR.getApiKey(), streamName, title, message, DESCRIPTOR.getHudsonUrl(), DESCRIPTOR.getSmartNotify());
    }

    private void initialize(String url, String email, String apiKey, String streamName, String title, String message, String hudsonUrl, boolean smartNotify) {
        humbug = new Humbug(url, email, apiKey);
        this.stream = streamName;
        this.title = title;
        this.message = message;
        if (hudsonUrl.length() > 0 && !hudsonUrl.endsWith("/") ) {
            hudsonUrl = hudsonUrl + "/";
        }
        this.hudsonUrl = hudsonUrl;
        this.smartNotify = smartNotify;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        // If SmartNotify is enabled, only notify if:
        //  (1) there was no previous build, or
        //  (2) the current build did not succeed, or
        //  (3) the previous build failed and the current build succeeded.
        smartNotify = DESCRIPTOR.getSmartNotify();
        if (smartNotify) {
            AbstractBuild previousBuild = build.getPreviousBuild();
            if (previousBuild == null ||
                build.getResult() != Result.SUCCESS ||
                previousBuild.getResult() != Result.SUCCESS)
            {
                publish(build, listener);
            }
        } else {
            publish(build, listener);
        }
        return true;
    }

    public String getHumbugStreamForProject() {
        return stream;
    }

    public String getHumbugTitleForProject() {
       return title;
    }

    public String getHumbugMessageForProject() {
       return message;
    }
}
