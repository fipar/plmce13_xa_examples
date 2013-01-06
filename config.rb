# Data source configuration for the examples
module Configuration

  DB_CONFIG_1 = {
    "class_name" => "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource",
    "url" => "jdbc:mysql://127.0.0.1:5527/test?user=msandbox&password=msandbox"
  }

  DB_CONFIG_2 = {
    "class_name" => "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource",
    "url" => "jdbc:mysql://127.0.0.1:5512/test?user=msandbox&password=msandbox"
  }


end

