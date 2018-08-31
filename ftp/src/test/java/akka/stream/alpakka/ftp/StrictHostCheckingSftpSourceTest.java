/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import java.net.InetAddress;

public class StrictHostCheckingSftpSourceTest extends SftpSourceTest {

  SftpSettings settings() throws Exception {
    // #create-settings
    final SftpSettings settings =
        SftpSettings.create(InetAddress.getByName("localhost"))
            .withPort(getPort())
            .withCredentials(
                new FtpCredentials.NonAnonFtpCredentials(
                    "different user and password", "will fail password auth"))
            .withStrictHostKeyChecking(true) // strictHostKeyChecking
            .withKnownHosts(getKnownHostsFile().getPath())
            .withSftpIdentity(
                SftpIdentity.createFileSftpIdentity(
                    getClientPrivateKeyFile().getPath(), CLIENT_PRIVATE_KEY_PASSPHRASE));
    // #create-settings
    return settings;
  }
}
