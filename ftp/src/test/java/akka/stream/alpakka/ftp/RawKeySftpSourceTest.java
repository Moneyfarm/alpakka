/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RawKeySftpSourceTest extends SftpSourceTest {

  SftpSettings settings() throws Exception {
    // #create-settings
    final SftpSettings settings =
        SftpSettings.create(InetAddress.getByName("localhost"))
            .withPort(getPort())
            .withStrictHostKeyChecking(false) // strictHostKeyChecking
            .withSftpIdentity(
                SftpIdentity.createRawSftpIdentity(
                    Files.readAllBytes(Paths.get(getClientPrivateKeyFile().getPath())),
                    CLIENT_PRIVATE_KEY_PASSPHRASE));
    // #create-settings
    return settings;
  }
}
