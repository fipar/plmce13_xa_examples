#!/usr/local/bin/jruby
# first basic example based on http://blog.endpoint.com/2010/07/distributed-transactions-and-two-phase.html

require 'java'
require 'config'

BTM = Java::BitronixTm::bitronixTransactionManager
TxnSvc = Java::BitronixTm::TransactionManagerServices
PDS = Java::BitronixTmResourceJdbc::PoolingDataSource

ds1 = PDS.new
ds1.set_class_name Configuration::DB_CONFIG_1["class_name"]
ds1.set_unique_name 'mysql1'
ds1.set_max_pool_size 3
ds1.get_driver_properties.set_property 'url', Configuration::DB_CONFIG_1["url"]
ds1.init

ds2 = PDS.new
ds2.set_class_name Configuration::DB_CONFIG_2["class_name"]
ds2.set_unique_name 'mysql2'
ds2.set_max_pool_size 3
ds2.get_driver_properties.set_property 'url', Configuration::DB_CONFIG_2["url"]
ds2.init


c1 = ds1.get_connection
c2 = ds2.get_connection

btm = TxnSvc.get_transaction_manager

btm.begin

begin
  s2 = c2.prepare_statement "INSERT INTO ledger VALUES (null,'Bob', 100)"
  s2.execute_update

  s1 = c1.prepare_statement "INSERT INTO ledger VALUES (11,'Alice', -100)"
  s1.execute_update

  btm.commit
  puts "Successfully committed"
rescue
  puts "Something bad happened: " + $!
  btm.rollback
end

btm.shutdown
