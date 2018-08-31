/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp;

import java.net.InetAddress;

public class PasswordSftpSourceTest extends SftpSourceTest {

  SftpSettings settings() throws Exception {
    // #create-settings
    final SftpSettings settings =
        SftpSettings.create(InetAddress.getByName("localhost"))
            .withPort(getPort())
            .withCredentials(
                new FtpCredentials.NonAnonFtpCredentials(
                    // SftpSupportImpl validates any matching username and password as valid
                    "user-pass-match", "user-pass-match"))
            .withStrictHostKeyChecking(false); // strictHostKeyChecking
    // #create-settings
    return settings;
  }
}
