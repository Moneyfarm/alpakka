/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.ftp
package impl

import java.io.{File, IOException, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermission

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.{OpenMode, RemoteResourceInfo, SFTPClient}
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.method._
import net.schmizz.sshj.userauth.password.{PasswordFinder, PasswordUtils, Resource}
import net.schmizz.sshj.xfer.FilePermission

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.Try

private[ftp] trait SftpOperations { _: FtpLike[SSHClient, SftpSettings] =>

  type Handler = SFTPClient

  def connect(connectionSettings: SftpSettings)(implicit ssh: SSHClient): Try[Handler] = Try {
    import connectionSettings._

    if (!strictHostKeyChecking)
      ssh.addHostKeyVerifier(new PromiscuousVerifier)
    else
      knownHosts.foreach(path => ssh.loadKnownHosts(new File(path)))

    ssh.connect(host.getHostAddress, port)

    // Auth methods will be tried in sequence until we either run out of options or access is granted.
    // Note that some servers require multiple forms of authentication and will return a a 'partial success' for the
    // correct auth methods until all required methods have been provided.
    val authMethods = buildAuthMethods(connectionSettings)
    ssh.auth(credentials.username, authMethods: _*)

    ssh.newSFTPClient()
  }

  def disconnect(handler: Handler)(implicit ssh: SSHClient): Unit = {
    handler.close()
    if (ssh.isConnected) ssh.disconnect()
  }

  def listFiles(basePath: String, handler: Handler): immutable.Seq[FtpFile] = {
    val path = if (!basePath.isEmpty && basePath.head != '/') s"/$basePath" else basePath
    val entries = handler.ls(path).asScala
    entries.map { file =>
      FtpFile(
        file.getName,
        file.getPath,
        file.isDirectory,
        file.getAttributes.getSize,
        file.getAttributes.getMtime * 1000L,
        getPosixFilePermissions(file)
      )
    }.toVector
  }

  private def getPosixFilePermissions(file: RemoteResourceInfo) = {
    import PosixFilePermission._

    import FilePermission._
    file.getAttributes.getPermissions.asScala.collect {
      case USR_R => OWNER_READ
      case USR_W => OWNER_WRITE
      case USR_X => OWNER_EXECUTE
      case GRP_R => GROUP_READ
      case GRP_W => GROUP_WRITE
      case GRP_X => GROUP_EXECUTE
      case OTH_R => OTHERS_READ
      case OTH_W => OTHERS_WRITE
      case OTH_X => OTHERS_EXECUTE
    }.toSet
  }

  def listFiles(handler: Handler): immutable.Seq[FtpFile] = listFiles(".", handler)

  def retrieveFileInputStream(name: String, handler: Handler): Try[InputStream] = Try {
    val remoteFile = handler.open(name, Set(OpenMode.READ).asJava)
    val is = new remoteFile.RemoteFileInputStream() {

      override def close(): Unit =
        try {
          super.close()
        } finally {
          remoteFile.close()
        }
    }
    Option(is).getOrElse {
      remoteFile.close()
      throw new IOException(s"$name: No such file or directory")
    }
  }

  def storeFileOutputStream(name: String, handler: Handler, append: Boolean): Try[OutputStream] = Try {
    import OpenMode._
    val openModes = Set(WRITE, CREAT) ++ (if (append) Set(APPEND) else Set())
    val remoteFile = handler.open(name, openModes.asJava)
    val os = new remoteFile.RemoteFileOutputStream() {

      override def close(): Unit = {
        try {
          remoteFile.close()
        } catch {
          case e: IOException =>
        }
        super.close()
      }
    }
    Option(os).getOrElse {
      remoteFile.close()
      throw new IOException(s"Could not write to $name")
    }
  }

  def move(fromPath: String, destinationPath: String, handler: Handler): Unit =
    handler.rename(fromPath, destinationPath)

  def remove(path: String, handler: Handler): Unit =
    handler.rm(path)

  private[this] def buildAuthMethods(connectionSettings: SftpSettings): Seq[AuthMethod] = {
    import connectionSettings._
    buildPasswordAuthMethods(credentials.password) ++ sftpIdentity.map(buildKeyAuthMethod)
  }

  private[this] def buildPasswordAuthMethods(password: String): Seq[AuthMethod] = {
    // this replicates the AuthMethods used by the underlying library for an 'authPassword' call
    val passwordFinder = new PasswordFinder() {
      override def reqPassword(resource: Resource[_]): Array[Char] = password.toCharArray
      override def shouldRetry(resource: Resource[_]): Boolean = false
    }
    Seq(new AuthPassword(passwordFinder), new AuthKeyboardInteractive(new PasswordResponseProvider(passwordFinder)))
  }

  private[this] def buildKeyAuthMethod(identity: SftpIdentity): AuthMethod = {
    def bats(array: Array[Byte]): String = new String(array, StandardCharsets.UTF_8.name())

    val passphrase = identity.privateKeyFilePassphrase.map(pass => PasswordUtils.createOneOff(bats(pass).toCharArray))

    val key = new OpenSSHKeyFile

    identity match {
      case id: RawKeySftpIdentity => key.init(bats(id.privateKey), id.publicKey.map(bats).orNull, passphrase.orNull)
      case id: KeyFileSftpIdentity => key.init(new File(id.privateKey), passphrase.orNull)
    }

    new AuthPublickey(key)
  }

}
