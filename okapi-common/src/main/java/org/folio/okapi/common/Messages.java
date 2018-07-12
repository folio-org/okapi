package org.folio.okapi.common;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Provide language specific messages, caching the language files in memory.
 */
public class Messages {
  /** directory in the jar file or in the current directory where to read the messages file from */
  public static final String      INFRA_MESSAGES_DIR     = "infra-messages";
  /** directory in the jar file or in the current directory where to read the
   * optional messages file of the project/module */
  public static final String      MESSAGES_DIR           = "messages";
  /** default language used for fall-back */
  public static final String      DEFAULT_LANGUAGE       = "en";
  private static String           language               = DEFAULT_LANGUAGE;

  private static final Logger log = LoggerFactory.getLogger(Messages.class);

  /** messageMap.get(language).getProperty(code) is the text */
  Map<String, Properties> messageMap       = new HashMap<>();

  Messages() {
    // throws exception on error
    loadAllMessages();
  }

  // will only be loaded (lazily) when getInstance() is called
  // once called the JVM (lazily) loads serially and returns - so this is thread
  // safe
  private static class SingletonHelper {
    private static final Messages INSTANCE = new Messages();
    private SingletonHelper() {
      // prevent instantiation
    }
  }

  /**
   * @return the singleton instance of Message
   */
  public static Messages getInstance() {
    return SingletonHelper.INSTANCE;
  }

  void loadAllMessages() {
    loadMessages(INFRA_MESSAGES_DIR);
    if (messageMap.isEmpty()) {
      throw new IllegalStateException("Messages not found: " + INFRA_MESSAGES_DIR);
    }
    //load project specific messages - they may not exist
    loadMessages(MESSAGES_DIR);
  }

  void loadMessages(String dir) {
    try {
      //load messages from the runtime jar
      URL url = Messages.class.getClassLoader().getResource(dir);
      if (url == null) {
        return;
      }
      URI uri = url.toURI();

      if ("jar".equals(uri.getScheme())) {
        // jar scheme is required for Jenkins:
        // https://github.com/folio-org/raml-module-builder/pull/111
        try (FileSystem fileSystem = getFileSystem(uri)) {
          Path messagePath = fileSystem.getPath(dir);
          loadMessages(messagePath);
        }
      } else {
        Path messagePath = Paths.get(uri);
        loadMessages(messagePath);
      }
    } catch (IOException|URISyntaxException e) {
      throw new IllegalArgumentException(dir, e);
    }
  }

  private FileSystem getFileSystem(URI uri) throws IOException {
    try {
      return FileSystems.newFileSystem(uri, Collections.<String, Object> emptyMap());
    } catch (FileSystemAlreadyExistsException e) { // NOSONAR
      return FileSystems.getFileSystem(uri);
    }
  }

  @SuppressWarnings("squid:S135") // suppress "Reduce the total number of break
  // "and continue statements in this loop to use at most one."
  protected void loadMessages(Path messagePath) throws IOException {
    try (Stream<Path> walk = Files.walk(messagePath, 1)) {
      for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
        Path file = it.next();
        String name = file.getFileName().toString();
        // APIMessages_de.properties or
        // de_APIMessages.prop
        int sep = name.indexOf('_');
        if (sep == -1) {
          continue;
        }
        int dot = name.indexOf('.', sep);
        if (dot == -1) {
          continue;
        }
        String chunk1 = name.substring(0, sep);
        String chunk2 = name.substring(sep + 1, dot);
        String lang = chunk2;
        if(chunk1.length() < chunk2.length()){
          lang = chunk1;
        }
        String resource = "/" + messagePath.getFileName().toString() + "/" + name;
        log.info("Loading messages from " + resource + " ................................");
        InputStream stream = getClass().getResourceAsStream(resource);
        Properties properties = new Properties();
        properties.load(stream);
        Properties existing = messageMap.get(lang);
        if(existing == null){
          messageMap.put(lang, properties);
        }
        else{
          existing.putAll(properties);
          messageMap.put(lang, existing);
        }
      }
    }
  }

  /**
   * Return the message from the properties file.
   *
   * @param language - the language of the properties file to search in
   * @param code - message code
   * @return the message, or null if not found
   */
  private String getMessageSingle(String language, String code) {
    Properties properties = messageMap.get(language);
    if (properties == null) {
      return null;
    }
    return properties.getProperty(code);
  }

  /**
   * Return the message from the properties file.
   * @param code - message code
   * @return the message, or null if not found
   */

  public String getMessage(String code) {
    String message = getMessageSingle(language, code);
    if (message != null) {
      return message;
    }
    return getMessageSingle(DEFAULT_LANGUAGE, code);
  }


  /**
   * Return the message from the properties file.
   * @param code - message code
   * @param messageArguments - message arguments to insert, see java.text.MessageFormat.format()
   * @return the message with arguments inserted
   */

  public String getMessage(String code, Object... messageArguments) {
    String pattern = getMessage(code);
    if (pattern == null) {
      return "Error message not found: " + language + " " + code;
    }
    return MessageFormat.format(pattern, messageArguments);
  }

  /** Set default language */
  public static void setLanguage(String language){
    Messages.language = language;
  }
}
