package oneke.aliyunoss;

import com.aliyun.oss.model.ObjectMetadata;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;

import com.aliyun.oss.OSSClient;

/**
 * @author leixu2txtek
 */
public class AliyunOSSBuilder extends Builder implements SimpleBuildStep {

    PrintStream logger;

    String BucketName;
    String FilePaths;

    public String getBucketName() {
        return BucketName;
    }

    public void setBucketName(String bucketName) {
        this.BucketName = bucketName;
    }

    public String getFilePaths() {
        return FilePaths;
    }

    public void setFilePaths(String filePaths) {
        this.FilePaths = filePaths;
    }

    @DataBoundConstructor
    public AliyunOSSBuilder(String bucketName, String filePaths) {

        this.BucketName = bucketName;
        this.FilePaths = filePaths;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {

        this.logger = listener.getLogger();
        boolean failed = build.getResult() == Result.FAILURE;

        if (failed) {

            logger.println("构建失败，无需上传静态资源");
            return;
        }

        //job's path
        File directory = new File(workspace.getRemote());

        if (!(directory.exists() && directory.isDirectory())) {

            build.setResult(Result.FAILURE);

            logger.println("构建失败，文件夹获取失败");
            return;
        }

        //region gets all upload path

        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<String> filePaths = new ArrayList<String>();

        for (String p : this.FilePaths.split(";")) {

            p = p.replace("\\", File.separator);

            filePaths.add(p);
            paths.add(new File(directory, p).getPath());
        }

        //if does not assign any path to upload, then upload the build workspace
        if (paths.size() == 0) paths.add(directory.getPath());

        logger.println(String.format("检索到 %s 个文件夹", paths.size()));


        //endregion

        //region gets all files

        ArrayList<String> files = new ArrayList<String>();
        for (String path : paths) Utils.GetFilesWithChildren(new File(path), files);

        //endregion

        //region get job name

        Map<String, String> environment = build.getEnvironment(listener);
        String jobName = environment.get("JOB_NAME");

        //endregion

        //region get ali oss client instance

        OSSClient client;
        {
            String endPoint = this.getDescriptor().getEndPointSuffix();
            String accessKey = this.getDescriptor().getAccessKey();
            String secretKey = this.getDescriptor().getSecretKey();

            client = new OSSClient(endPoint, accessKey, secretKey);
        }

        //endregion

        //region remove current job's files in ali oss

        String prefix = String.format("public/%s/", jobName);

        try {

            client.deleteObject(this.getBucketName(), prefix);
        } catch (Exception ex) {

            build.setResult(Result.FAILURE);

            logger.println(String.format("删除 %s 目录下文件失败", prefix));
            return;
        }

        logger.println(String.format("删除 %s 目录下文件成功", prefix));

        //endregion

        String root = String.format("%s%s", directory.getAbsolutePath(), File.separator);

        for (String file : files) {

            //region create upload meta

            File tmp = new File(file);
            InputStream content = new FileInputStream(tmp);

            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(tmp.length());

            //endregion

            //region resolve the object key (path?)

            file = file.replace(root, "");

            for (String t : filePaths) file = file.replace(t, "");

            if (file.indexOf(File.separator) != -1) file = file.substring(1);

            //upload objects in public directory
            file = String.format("%s%s", prefix, file);

            logger.println(String.format("正在上传资源：%s", file));

            //endregion

            // upload file
            client.putObject(this.getBucketName(), file, content, meta);
        }

        logger.println(String.format("成功上传 %s 个静态资源", files.size()));
    }

    @Override
    public DescriptorImpl getDescriptor() {

        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * OSS AccessKey
         */
        private String AccessKey;

        /**
         * OSS SecretKey
         */
        private String SecretKey;

        /**
         * OSS EndPointSuffix
         */
        private String EndPointSuffix;

        public String getAccessKey() {

            return AccessKey;
        }

        public void setAccessKey(String accessKey) {

            this.AccessKey = accessKey;
        }

        public String getSecretKey() {

            return SecretKey;
        }

        public void setSecretKey(String secretKey) {

            this.SecretKey = secretKey;
        }

        public String getEndPointSuffix() {

            return EndPointSuffix;
        }

        public void setEndPointSuffix(String endPointSuffix) {

            this.EndPointSuffix = endPointSuffix;
        }

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {

            load();
        }

        /**
         * Check Ali OSS account
         *
         * @param AccessKey
         * @param SecretKey
         * @param EndPointSuffix
         * @return Validation Message
         */
        public FormValidation doCheckAccount(@QueryParameter String AccessKey, @QueryParameter String SecretKey,
                                             @QueryParameter String EndPointSuffix) {

            if (AccessKey == null || AccessKey.matches("\\s*")) {

                return FormValidation.error("阿里云AccessKey不能为空！");
            }

            if (SecretKey == null || SecretKey.matches("\\s*")) {

                return FormValidation.error("阿里云SecretKey不能为空！");
            }

            if (EndPointSuffix == null || EndPointSuffix.matches("\\s*")) {
                return FormValidation.error("阿里云EndPointSuffix不能为空！");
            }

            //region validate oss account

            try {

                OSSClient client = new OSSClient(EndPointSuffix, AccessKey, SecretKey);
                client.listBuckets();

            } catch (Exception e) {

                return FormValidation.error("阿里云帐号验证失败！");
            }

            //endregion

            return FormValidation.ok("验证阿里云帐号成功！");
        }

        /**
         * If true that this builder can be used with all kinds of project types
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {

            return true;
        }

        /**
         * Set the builder's name
         */
        public String getDisplayName() {
            return "将静态资源上传至 Ali OSS";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

            req.bindParameters(this);

            this.AccessKey = formData.getString("AccessKey");
            this.SecretKey = formData.getString("SecretKey");
            this.EndPointSuffix = formData.getString("EndPointSuffix");

            save();

            return super.configure(req, formData);
        }
    }
}
