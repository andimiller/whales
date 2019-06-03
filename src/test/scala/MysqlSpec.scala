import java.io.File
import java.nio.file.Path

import cats._
import implicits._
import effect._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import net.andimiller.whales._
import net.andimiller.whales.aquarium.MySQL
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.ExecutionContext.global

class MysqlSpec extends FlatSpec with MustMatchers {

  implicit val timer = IO.timer(global)
  implicit val cs    = IO.contextShift(global)

  case class Cat(name: String, age: Int)
  def insertCat(c: Cat) = sql"insert into cat values(${c.name}, ${c.age})".update.run
  def listCats()        = sql"select * from cat".query[Cat].stream.compile.toList

  "the mysql from the aquarium" should "spin up and populate" in {
    MySQL
      .v8[IO](
        MySQL
          .MySQLConfiguration()
          .withDatabase("vetdb")
          .withUser(MySQL.User("vet", "vetpw"))
          .withSqlDirectory(new File(getClass.getResource("mysql/vet-database/").getPath).toPath)
      )
      .use { db =>
        val xa = Transactor.fromDriverManager[IO](
          "com.mysql.cj.jdbc.Driver",
          s"jdbc:mysql://${db.ipAddress}:3306/vetdb",
          "vet",
          "vetpw"
        )

        Thread.sleep(1000)

        listCats.transact(xa).map(_ must equal(List())) *>
          insertCat(Cat("Bob", 4)).transact(xa).map(_ must equal(1)) *>
          listCats.transact(xa).map(_ must equal(List(Cat("Bob", 4))))
      }
      .unsafeRunSync()
  }

}
