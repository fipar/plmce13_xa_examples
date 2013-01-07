#!/usr/local/bin/jruby
# very simple money transfer XA example
# will attempt to transfer $500 from account 1 on BancoRepublica to account 1 on LoweCreditUnion
# should fail with the example data set

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
  puts "checking balance"
  rs = c2.create_statement.execute_query "SELECT * FROM xa_examples.accounts WHERE id = 1 AND balance > 500 "
  raise Exception, "Insufficient funds" unless rs.next
  puts "recording withdrawal"
  c2.create_statement.execute_update "INSERT INTO xa_examples.transactions (account_id,amount) VALUES (1,-500)"
  puts "widthdrawing funds"
  c2.create_statement.execute_update "UPDATE xa_examples.accounts SET balance = balance - 500 WHERE id = 1"
  puts "recording credit"
  c1.create_statement.execute_update "INSERT INTO xa_examples.transactions (account_id,amount) VALUES (1,500)"
  puts "crediting funds"
  c1.create_statement.execute_update "UPDATE xa_examples.accounts SET balance = balance + 500 WHERE id = 1"
  btm.commit
  puts "Successfully finished money transfer"
rescue
  puts "Something bad happened while attempting the transfer: " + $!
  btm.rollback
end

btm.shutdown
