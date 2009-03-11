/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.util.io;// 5724-D15

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Plugin;

import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.NestedJarFileModule;
import com.ibm.wala.core.plugin.CorePlugin;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;

/**
 * 
 * This class provides files that are packaged with this plug-in
 * 
 * @author sfink
 */
public class FileProvider {

  private final static int DEBUG_LEVEL = 0;

  public FileProvider() {
    super();
  }

  /**
   * @return null if there's a problem
   */
  public static IWorkspace getWorkspace() {
    try {
      return ResourcesPlugin.getWorkspace();
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * @param fileName
   * @return the jar file packaged with this plug-in of the given name, or null if not found.
   */
  public static Module getJarFileModule(String fileName) throws IOException {
    return getJarFileModule(fileName, FileProvider.class.getClassLoader());
  }

  public static Module getJarFileModule(String fileName, ClassLoader loader) throws IOException {
    if (CorePlugin.getDefault() == null) {
      return getJarFileFromClassLoader(fileName, loader);
    } else {
      // try to load the path as a full path
      try {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IFile file = workspaceRoot.getFile(new Path(fileName));
        if (file != null) {
          return new JarFileModule(new JarFile(fileName, false));
        }
      } catch (Exception e) {
      }

      // otherwise load from plugin
      return getFromPlugin(CorePlugin.getDefault(), fileName);
    }

  }

  public static URL getResource(String fileName) throws IOException {
    return getResource(fileName, FileProvider.class.getClassLoader());
  }

  public static URL getResource(String fileName, ClassLoader loader) throws IOException {
    if (CorePlugin.getDefault() == null && loader == null) {
      throw new IllegalArgumentException("null loader");
    }
    return (CorePlugin.getDefault() == null) ? loader.getResource(fileName) : FileLocator.find(CorePlugin.getDefault().getBundle(),
        new Path(fileName), null);
  }

  /**
   */
  public static File getFile(String fileName) throws IOException {
    return getFile(fileName, FileProvider.class.getClassLoader());
  }

  public static File getFile(String fileName, ClassLoader loader) throws IOException {
    return (CorePlugin.getDefault() == null) ? getFileFromClassLoader(fileName, loader) : getFileFromPlugin(
        CorePlugin.getDefault(), fileName);
  }

  /**
   * @param fileName
   * @return the jar file packaged with this plug-in of the given name, or null if not found.
   * @throws IllegalArgumentException if p is null
   */
  public static File getFileFromPlugin(Plugin p, String fileName) throws IOException {

    if (p == null) {
      throw new IllegalArgumentException("p is null");
    }
    URL url = getFileURLFromPlugin(p, fileName);
    if (url == null) {
      throw new FileNotFoundException(fileName);
    }
    return new File(filePathFromURL(url));
  }

  /**
   * @param fileName
   * @return the jar file packaged with this plug-in of the given name, or null if not found.
   */
  private static JarFileModule getFromPlugin(Plugin p, String fileName) throws IOException {
    URL url = getFileURLFromPlugin(p, fileName);
    return (url == null) ? null : new JarFileModule(new JarFile(filePathFromURL(url)));
  }

  /**
   * get a file URL for a file from a plugin
   * 
   * @param fileName the file name
   * @return the URL, or <code>null</code> if the file is not found
   * @throws IOException
   */
  private static URL getFileURLFromPlugin(Plugin p, String fileName) throws IOException {
    URL url = FileLocator.find(p.getBundle(), new Path(fileName), null);
    if (url == null) {
      // try lib/fileName
      String libFileName = "lib/" + fileName;
      url = FileLocator.find(p.getBundle(), new Path(libFileName), null);
      if (url == null) {
        // try bin/fileName
        String binFileName = "bin/" + fileName;
        url = FileLocator.find(p.getBundle(), new Path(binFileName), null);
        if (url == null) {
          // try it as an absolute path?
          File f = new File(fileName);
          if (!f.exists()) {
            // give up
            return null;
          } else {
            url = f.toURI().toURL();
          }
        }
      }
    }
    url = FileLocator.toFileURL(url);
    url = fixupFileURLSpaces(url);
    return url;
  }

  /**
   * escape spaces in a URL, primarily to work around a bug in {@link File#toURL()}
   * 
   * @param url
   * @return an escaped version of the URL
   */
  private static URL fixupFileURLSpaces(URL url) {
    String urlString = url.toExternalForm();
    StringBuffer fixedUpUrl = new StringBuffer();
    int lastIndex = 0;
    while (true) {
      int spaceIndex = urlString.indexOf(' ', lastIndex);

      if (spaceIndex < 0) {
        fixedUpUrl.append(urlString.substring(lastIndex));
        break;
      }

      fixedUpUrl.append(urlString.substring(lastIndex, spaceIndex));
      fixedUpUrl.append("%20");
      lastIndex = spaceIndex + 1;
    }
    try {
      return new URL(fixedUpUrl.toString());
    } catch (MalformedURLException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
    return null;
  }

  /**
   * @throws FileNotFoundException
   */
  public static File getFileFromClassLoader(String fileName, ClassLoader loader) throws FileNotFoundException {
    URL url = loader.getResource(fileName);
    if (DEBUG_LEVEL > 0) {
      Trace.println("FileProvider got url: " + url + " for " + fileName);
    }
    if (url == null) {
      // couldn't load it from the class loader. try again from the
      // system classloader
      File f = new File(fileName);
      if (f.exists()) {
        return f;
      }
      throw new FileNotFoundException(fileName);
    } else {
      return new File(filePathFromURL(url));
    }
  }

  /**
   * @param fileName
   * @return the jar file packaged with this plug-in of the given name, or null if not found: wrapped as a JarFileModule
   *         or a NestedJarFileModule
   * @throws IOException
   */
  public static Module getJarFileFromClassLoader(String fileName, ClassLoader loader) throws IOException {
    URL url = loader.getResource(fileName);
    if (DEBUG_LEVEL > 0) {
      Trace.println("FileProvider got url: " + url + " for " + fileName);
    }
    if (url == null) {
      // couldn't load it from the class loader. try again from the
      // system classloader
      try {
        return new JarFileModule(new JarFile(fileName, false));
      } catch (ZipException e) {
        throw new IOException("Could not find file: " + fileName);
      }
    }
    if (url.getProtocol().equals("jar")) {
      JarURLConnection jc = (JarURLConnection) url.openConnection();
      JarFile f = jc.getJarFile();
      JarEntry entry = jc.getJarEntry();
      JarFileModule parent = new JarFileModule(f);
      return new NestedJarFileModule(parent, entry);
    } else {
      String filePath = filePathFromURL(url);
      return new JarFileModule(new JarFile(filePath, false));
    }
  }

  /**
   * Properly creates the String file name of a {@link URL}. This works around a bug in the Sun implementation of
   * {@link URL#getFile()}, which doesn't properly handle file paths with spaces (see <a
   * href="http://sourceforge.net/tracker/index.php?func=detail&aid=1565842&group_id=176742&atid=878458">bug report</a>).
   * For now, fails with an assertion if the url is malformed.
   * 
   * @param url
   * @return the path name for the url
   * @throws IllegalArgumentException if url is null
   */
  public static String filePathFromURL(URL url) {
    if (url == null) {
      throw new IllegalArgumentException("url is null");
    }
    URI uri = null;
    try {
      uri = new URI(url.toString());
    } catch (URISyntaxException e) {
      Assertions.UNREACHABLE();
    }
    String filePath = uri.getPath();
    return filePath;
  }

}
