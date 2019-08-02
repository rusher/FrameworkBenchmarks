const cluster = require('cluster');
const numCPUs = require('os').cpus().length;

process.env.NODE_HANDLER = 'mysql-raw';

switch (process.env.TFB_TEST_NAME) {
  case 'nodejs-mongodb':
    process.env.NODE_HANDLER = 'mongoose';
    break;
  case 'nodejs-mongodb-raw':
    process.env.NODE_HANDLER = 'mongodb-raw';
    break;
  case 'nodejs-mysql':
    process.env.NODE_HANDLER = 'sequelize';
    break;
  case 'nodejs-postgres':
    process.env.NODE_HANDLER = 'sequelize-postgres';
    break;
  case 'nodejs-mariadb':
    process.env.NODE_HANDLER = 'sequelize-mariadb';
    break;
  case 'nodejs-mariadb-raw':
    process.env.NODE_HANDLER = 'mariadb-raw';
    break;
}

if (cluster.isMaster) {
  // Fork workers.
  for (let i = 0; i < numCPUs; i++) {
    cluster.fork();
  }

  cluster.on('exit', (worker, code, signal) => {
  	console.log([
  	  'A process exit was triggered, most likely due to a failed database action',
  	  'NodeJS test server shutting down now'].join('\n'));
    process.exit(1);
  });
} else {
  // Task for forked worker
  require('./create-server');
}
