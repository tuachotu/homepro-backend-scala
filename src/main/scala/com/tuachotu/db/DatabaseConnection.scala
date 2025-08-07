package com.tuachotu.db

import com.tuachotu.util.{ConfigUtil, LoggerUtil}
import com.tuachotu.util.LoggerUtil.getLogger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.{Connection, PreparedStatement, ResultSet}
import javax.sql.DataSource
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try, Using}

object DatabaseConnection {
  private lazy val dataSource: DataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl(ConfigUtil.getString("database.url"))
    config.setUsername(ConfigUtil.getString("database.user"))
    config.setPassword(ConfigUtil.getString("database.password"))
    config.setDriverClassName("org.postgresql.Driver")
    config.setMaximumPoolSize(ConfigUtil.getInt("database.maxPoolSize", 10))
    config.setMinimumIdle(ConfigUtil.getInt("database.minIdle", 5))
    config.setConnectionTimeout(ConfigUtil.getLong("database.connectionTimeout", 30000))
    config.setIdleTimeout(ConfigUtil.getLong("database.idleTimeout", 600000))
    config.setMaxLifetime(ConfigUtil.getLong("database.maxLifetime", 1800000))
    
    implicit val clazz: Class[?] = classOf[DatabaseConnection.type]
    LoggerUtil.info("Initializing database connection pool", 
      "maxPoolSize", config.getMaximumPoolSize, 
      "minIdle", config.getMinimumIdle)
      
    new HikariDataSource(config)
  }

  def getConnection: Connection = dataSource.getConnection

  def withConnection[T](operation: Connection => T): Try[T] = {
    Using(getConnection)(operation)
  }

  def withConnectionAsync[T](operation: Connection => T)(implicit ec: ExecutionContext): Future[T] = {
    Future {
      withConnection(operation) match {
        case Success(result) => result
        case Failure(exception) => 
          implicit val clazz: Class[?] = classOf[DatabaseConnection.type]
          LoggerUtil.error("Database operation failed", exception)
          throw exception
      }
    }
  }

  def executeQuery[T](sql: String, params: Any*)(resultMapper: ResultSet => T)(implicit ec: ExecutionContext): Future[List[T]] = {
    withConnectionAsync { connection =>
      Using.Manager { use =>
        val statement = use(connection.prepareStatement(sql))
        setParameters(statement, params*)
        val resultSet = use(statement.executeQuery())
        
        val results = scala.collection.mutable.ListBuffer[T]()
        while (resultSet.next()) {
          results += resultMapper(resultSet)
        }
        results.toList
      }.get
    }
  }

  def executeQuerySingle[T](sql: String, params: Any*)(resultMapper: ResultSet => T)(implicit ec: ExecutionContext): Future[Option[T]] = {
    executeQuery(sql, params*)(resultMapper).map(_.headOption)
  }

  def executeUpdate(sql: String, params: Any*)(implicit ec: ExecutionContext): Future[Int] = {
    withConnectionAsync { connection =>
      Using.Manager { use =>
        val statement = use(connection.prepareStatement(sql))
        setParameters(statement, params*)
        statement.executeUpdate()
      }.get
    }
  }

  def executeInsert[T](sql: String, params: Any*)(keyMapper: ResultSet => T)(implicit ec: ExecutionContext): Future[T] = {
    withConnectionAsync { connection =>
      Using.Manager { use =>
        val statement = use(connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS))
        setParameters(statement, params*)
        statement.executeUpdate()
        
        val generatedKeys = use(statement.getGeneratedKeys)
        if (generatedKeys.next()) {
          keyMapper(generatedKeys)
        } else {
          throw new RuntimeException("No generated keys returned")
        }
      }.get
    }
  }

  private def setParameters(statement: PreparedStatement, params: Any*): Unit = {
    params.zipWithIndex.foreach { case (param, index) =>
      val paramIndex = index + 1
      param match {
        case null => statement.setNull(paramIndex, java.sql.Types.NULL)
        case value: String => statement.setString(paramIndex, value)
        case value: Int => statement.setInt(paramIndex, value)
        case value: Long => statement.setLong(paramIndex, value)
        case value: Double => statement.setDouble(paramIndex, value)
        case value: Boolean => statement.setBoolean(paramIndex, value)
        case value: java.sql.Timestamp => statement.setTimestamp(paramIndex, value)
        case value: java.util.UUID => statement.setObject(paramIndex, value)
        case Some(value) => setParameters(statement, value)
        case None => statement.setNull(paramIndex, java.sql.Types.NULL)
        case value => statement.setObject(paramIndex, value)
      }
    }
  }

  def close(): Unit = {
    dataSource match {
      case hikariDataSource: HikariDataSource => hikariDataSource.close()
      case _ => // Do nothing for other data source types
    }
  }
}
