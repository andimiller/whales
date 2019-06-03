package net.andimiller.whales.aquarium

import java.nio.file.Path

import cats._
import cats.implicits._
import cats.effect._
import net.andimiller.whales._
import net.andimiller.whales.syntax._

object MySQL {

  case class User(username: String, password: String)
  case class MySQLConfiguration(
      baseConfig: DockerImage = DockerImage(
        "mysql",
        "8",
        name = Some("mysql"),
        ports = List(3306)
      ),
      rootPassword: Option[String] = None,
      createDatabase: Option[String] = None,
      createUser: Option[User] = None,
      sqlDirectory: Option[Path] = None,
      bindToHost: Boolean = false
  ) {
    def withRootPassword(password: String): MySQLConfiguration = copy(rootPassword = Some(password))
    def withDatabase(name: String): MySQLConfiguration         = copy(createDatabase = Some(name))
    def withUser(user: User): MySQLConfiguration               = copy(createUser = Some(user))
    def withSqlDirectory(path: Path): MySQLConfiguration       = copy(sqlDirectory = Some(path))
    def withBaseConfig(image: DockerImage): MySQLConfiguration = copy(baseConfig = image)
    def withBindToHost: MySQLConfiguration                     = copy(bindToHost = true)
  }

  def v8[F[_]: Effect: Timer](config: MySQLConfiguration): Resource[F, DockerContainer] =
    for {
      docker <- Docker[F]
      extraEnv = List(
        Option(config.rootPassword.map("MYSQL_ROOT_PASSWORD" -> _).getOrElse("MYSQL_RANDOM_ROOT_PASSWORD" -> "true")),
        config.createDatabase.map("MYSQL_DATABASE" -> _),
        config.createUser.map("MYSQL_USER"         -> _.username),
        config.createUser.map("MYSQL_PASSWORD"     -> _.password)
      ).flatten.toMap
      volume = List(
        config.sqlDirectory.map(_.toAbsolutePath.toString -> "/docker-entrypoint-initdb.d")
      ).flatten.toMap
      mysql <- docker(
                config.baseConfig.copy(
                  env = extraEnv ++ config.baseConfig.env,
                  volumes = volume ++ config.baseConfig.volumes,
                  bindings = (if (config.bindToHost) Map(3306.tcp -> Binding(hostname = Some("0.0.0.0"))) else Map.empty) ++ config.baseConfig.bindings
                )
              )
      _ <- mysql.waitForPort[F](3306, backoffs = 8)
    } yield mysql

}
