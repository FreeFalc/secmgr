// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.secmgr.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * A base class for implementing a configuration encode/decode class.
 */
public abstract class ConfigCodec {

  /**
   * Decode a configuration from a string.
   *
   * @param string The string to decode.
   * @return A configuration.
   * @throws IOException if there are errors while reading the configuration.
   * @throws ConfigException if the configuration is malformed.
   */
  public SecurityManagerConfig readConfig(String string)
      throws IOException, ConfigException {
    return readConfig(new StringReader(string));
  }

  /**
   * Encode a configuration as a string.
   *
   * @param config The configuration to be encoded.
   * @return The encoded string.
   * @throws IOException if there are errors while writing the configuration.
   */
  public String configToString(SecurityManagerConfig config) throws IOException {
    Writer writer = new StringWriter();
    writeConfig(config, writer);
    return writer.toString();
  }

  /**
   * Read and decode a configuration from a given source.
   *
   * @param reader The source to read the encoded configuration from.
   * @return A configuration.
   * @throws IOException if there are errors while reading the configuration.
   * @throws ConfigException if the configuration is malformed.
   */
  public SecurityManagerConfig readConfig(Reader reader)
      throws IOException, ConfigException {
    SecurityManagerConfig config = readConfigInternal(reader);
    guaranteeValidConfig(config);
    return config;
  }

  /**
   * Read and decode a configuration from a given file.
   *
   * @param file The file to read the encoded configuration from.
   * @return A configuration.
   * @throws IOException if there are errors while reading the configuration.
   * @throws ConfigException if the configuration is malformed.
   */
  public SecurityManagerConfig readConfig(File file)
      throws IOException, ConfigException {
    Reader reader = new FileReader(file);
    try {
      return readConfig(reader);
    } finally {
      reader.close();
    }
  }

  protected abstract SecurityManagerConfig readConfigInternal(Reader reader)
      throws IOException, ConfigException;

  /**
   * Encode and write a configuration to a given sink.
   *
   * @param config The configuration to be encoded.
   * @param writer The sink to write the encoded configuration to.
   * @throws IOException if there are errors while writing the configuration.
   */
  public void writeConfig(SecurityManagerConfig config, Writer writer) throws IOException {
    guaranteeValidConfig(config);
    writeConfigInternal(config, writer);
  }

  protected abstract void writeConfigInternal(SecurityManagerConfig config, Writer writer)
      throws IOException;

  private void guaranteeValidConfig(SecurityManagerConfig config) {
    if (!isValidConfig(config)) {
      throw new IllegalStateException("Config is not valid: " + config);
    }
  }

  /**
   * A valid config must contain a "default" group and that group must require a
   * username, be non-optional, and non-empty.  This in turn ensures that a saml
   * assertion from the security manager will always contain a username which
   * can in turn be used when creating policy ACLs.
   *
   * TODO: ensure that EFE gets the right username and/or that
   * the correct saml subject is generated by the security manager.
   */
  private boolean isValidConfig(SecurityManagerConfig config) {
    return true;
    /* TODO: This check was added
     * to address the concern that PVI assignment may not be stable- specifically,
     * user1 has a forms auth cookie before any interaction with the GSA, if the
     * default CG does not insist on requires-username, then CG will be satisfied
     * but no username is set, so the PVI could come from somewhere else (e.g.
     * kerberos).  However, user2 has no cookie, so they get a ULF, and the PVI
     * is set by their forms login.  That is a problem, however, this solution
     * is too drastic, and eliminates numerous valid use-cases (such as a sec-mgr
     * that does nothing but a single forms-auth CG that doesn't need a username).
     * So we're going to have to find a more subtle solution to the original
     * problem.
     */
  }
}
