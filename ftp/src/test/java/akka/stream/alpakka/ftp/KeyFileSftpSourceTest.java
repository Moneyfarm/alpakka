/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import java.net.InetAddress;

public class KeyFileSftpSourceTest extends SftpSourceTest {

  SftpSettings settings() throws Exception {
    // #create-settings
    final SftpSettings settings =
        SftpSettings.create(InetAddress.getByName("localhost"))
            .withPort(getPort())
            .withStrictHostKeyChecking(false) // strictHostKeyChecking
            .withSftpIdentity(
                SftpIdentity.createFileSftpIdentity(
                    getClientPrivateKeyFile().getPath(), CLIENT_PRIVATE_KEY_PASSPHRASE));
    // #create-settings
    return settings;
  }
}
