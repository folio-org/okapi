package org.folio.okapi.common;

import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.Logger;

public class ModuleVersionReporter {

  private String version;
  private String artifact;
  private String gitCommitId;
  private String gitRemoteOriginUrl;

  private final Logger logger = OkapiLogger.get();

  public ModuleVersionReporter(String path) {
    readProperties(path, "git.properties");
  }

  public ModuleVersionReporter(String path, String gitProperties) {
    readProperties(path, gitProperties);
  }

  private void readProperties(String path, String gitProperties) {
    try {
      final String fp = "META-INF/maven/" + path + "/pom.properties";
      InputStream in = getClass().getClassLoader()
          .getResourceAsStream(fp);
      if (in != null) {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        version = prop.getProperty("version");
        artifact = prop.getProperty("artifactId");
      } else {
        logger.warn("{} not found", fp);
      }
      in = getClass().getClassLoader().getResourceAsStream(gitProperties);
      if (in != null) {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        gitCommitId = prop.getProperty("git.commit.id");
        gitRemoteOriginUrl = prop.getProperty("git.remote.origin.url");
      } else {
        logger.warn("{} not found", gitProperties);
      }
    } catch (Exception ex) {
      logger.warn(ex);
    }
  }

  public String getModule() {
    return artifact;
  }

  public String getVersion() {
    return version;
  }

  public String getCommitId() {
    return gitCommitId;
  }

  public String getRemoteOriginUrl() {
    return gitRemoteOriginUrl;
  }

  public void logStart() {
    logger.info("Module {} {} started", getModule(), getVersion());
    logger.info("git: {} {}", gitRemoteOriginUrl, getCommitId());
  }
}
