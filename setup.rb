#!/usr/local/bin/jruby
# sets up the database for the examples

require 'java'
require 'config'

drop_database_xa_examples = "DROP DATABASE IF EXISTS xa_examples"

create_database_xa_examples = "CREATE DATABASE IF NOT EXISTS xa_examples"

drop_table_accounts = "DROP TABLE IF EXISTS xa_examples.accounts"

create_table_accounts = "
   CREATE TABLE IF NOT EXISTS xa_examples.accounts (
     id int unsigned not null auto_increment primary key, 
     holder_name tinytext not null, 
     balance decimal (10,2) not null default 0
   ) Engine=Innodb
"

drop_table_transactions = "DROP TABLE IF EXISTS xa_examples.transactions"

create_table_transactions = "
   CREATE TABLE IF NOT EXISTS xa_examples.transactions (
     id int unsigned not null auto_increment primary key, 
     account_id int unsigned not null, 
     amount decimal(10,2) not null, 
     FOREIGN KEY (account_id) REFERENCES xa_examples.accounts(id)
   ) Engine=Innodb
"

BTM = Java::BitronixTm::bitronixTransactionManager
TxnSvc = Java::BitronixTm::TransactionManagerServices
PDS = Java::BitronixTmResourceJdbc::PoolingDataSource

ds1 = PDS.new
ds1.set_class_name Configuration::DB_CONFIG_1["class_name"]
ds1.set_unique_name 'mysql1'
ds1.set_max_pool_size 3
ds1.get_driver_properties.set_property 'url', Configuration::DB_CONFIG_1["url"]
ds1.set_allow_local_transactions true
ds1.init

ds2 = PDS.new
ds2.set_class_name Configuration::DB_CONFIG_2["class_name"]
ds2.set_unique_name 'mysql2'
ds2.set_max_pool_size 3
ds2.get_driver_properties.set_property 'url', Configuration::DB_CONFIG_2["url"]
ds2.set_allow_local_transactions true
ds2.init


c1 = ds1.get_connection
c2 = ds2.get_connection

#btm = TxnSvc.get_transaction_manager
#btm.begin

@conns = [c1, c2]

begin
  @conns.each do |conn|
    conn.create_statement.execute_update drop_database_xa_examples
    conn.create_statement.execute_update create_database_xa_examples
    conn.create_statement.execute_update drop_table_accounts
    conn.create_statement.execute_update drop_table_transactions
    conn.create_statement.execute_update create_table_accounts
    conn.create_statement.execute_update create_table_transactions
  end 

# btm.commit
 puts "Successfully created sample database"
rescue
  puts "Something bad happened: " + $!
#  btm.rollback
end

#btm.shutdown
