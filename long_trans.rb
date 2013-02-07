#!/usr/local/bin/jruby
# very simple money transfer XA example
# will transfer $100 from account 1 on LoweCreditUnion to account 2 on BancoRepublica
# should succeed with the example data set

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
  puts "Checking source account balance"
  rs = c1.create_statement.execute_query "SELECT * FROM xa_examples.accounts WHERE id = 1 AND balance > 100 "
  btm.rollback unless rs.next
  puts "recording withdrawal"
  c1.create_statement.execute_update "INSERT INTO xa_examples.transactions (account_id,amount) VALUES (1,-100)"
  puts "widthdrawing funds"
  c1.create_statement.execute_update "UPDATE xa_examples.accounts SET balance = balance - 100 WHERE id = 1"
  puts "recording credit"
  c2.create_statement.execute_update "INSERT INTO xa_examples.transactions (account_id,amount) VALUES (2,100)"
  puts "crediting funds"
  c2.create_statement.execute_update "UPDATE xa_examples.accounts SET balance = balance + 100 WHERE id = 2"
  sleep 200
  btm.commit
  puts "Successfully finished money transfer"
rescue
  puts "Something bad happened while attempting the transfer: " + $!
  btm.rollback
end

btm.shutdown
