const h = require('../helper');
const mariadb = require('mariadb');

const pool = mariadb.createPool({
  host: 'tfb-database',
  user: 'benchmarkdbuser',
  password: 'benchmarkdbpass',
  database: 'hello_world',
  connectionLimit: 100,
  port: 3307
});

const NodeCache = require("node-cache");
const myCache = new NodeCache({ stdTTL: 0, checkperiod: 0 });

let cachePopulated = false;


const queries = {
  GET_RANDOM_WORLD: "SELECT * FROM world WHERE id = ?",
  ALL_FORTUNES: "SELECT * FROM fortune",
  ALL_WORLDS: "SELECT * FROM world",
  UPDATE_WORLD: "UPDATE world SET randomNumber = ? WHERE id = ?"
};

const populateCache = (callback) => {
  if (cachePopulated) return callback();
  pool.query(queries.ALL_WORLDS)
    .then(rows => {
      rows.forEach(r =>
        myCache.set(r.id, { id: r.id, randomNumber: r.randomNumber }));
      cachePopulated = true;
      callback();
    }).catch((err) => {
      console.log(err);
      process.exit(1);
    });
};

const randomWorldPromise = () =>
  pool.query(queries.GET_RANDOM_WORLD, [h.randomTfbNumber()])
    .then( rows => {
      return rows[0];
    }).catch((err) => {
      console.log(err);
      process.exit(1);
    });

const getAllFortunesPromise = () =>
  pool.query(queries.ALL_FORTUNES)
    .then( rows => {
        rows.meta = undefined;
      return rows;
    })
    .catch((err) => {
      console.log(err);
      process.exit(1);
    });

const updateQueryPromise = () =>
  pool.query(queries.GET_RANDOM_WORLD, [h.randomTfbNumber()])
      .then( rows => {
          const random = h.randomTfbNumber();
          return pool.query(queries.UPDATE_WORLD, [random, rows[0]['id']])
              .then( rows2 => {
                  rows[0].randomNumber = random;
                  return rows[0];
              })
              .catch((err) => {
                  console.log(err);
                  process.exit(1);
              });

      })
      .catch((err) => {
        console.log(err);
        process.exit(1);
      });

module.exports = {

  SingleQuery: (req, res) => {
    randomWorldPromise().then((world) => {
      h.addTfbHeaders(res, 'json');
      res.end(JSON.stringify(world));
    });
  },

  MultipleQueries: (queries, req, res) => {
    const worldPromises = [];

    for (let i = 0; i < queries; i++) {
      worldPromises.push(randomWorldPromise());
    }

    Promise.all(worldPromises).then((worlds) => {
      h.addTfbHeaders(res, 'json');
      res.end(JSON.stringify(worlds));
    });
  },
  Fortunes: (req, res) => {
      getAllFortunesPromise()
      .then(fortunes => {
        fortunes.push(h.additionalFortune());
        fortunes.sort((a, b) => a.message.localeCompare(b.message));
        h.addTfbHeaders(res, 'html');
        res.end(h.fortunesTemplate({
          fortunes: fortunes
        }));
      });
  },

  Updates: (queries, req, res) => {
    const worldPromises = [];

    for (let i = 0; i < queries; i++) {
      worldPromises.push(updateQueryPromise());
    }

    Promise.all(worldPromises).then(results => {
      h.addTfbHeaders(res, 'json');
      res.end(JSON.stringify(results));
    });
  },

  CachedQueries: (queries, req, res) => {
    populateCache(() => {
      let worlds = [];
      for (let i = 0; i < queries; i++) {
        const key = h.randomTfbNumber() + '';
        worlds.push(myCache.get(key));
      }

      h.addTfbHeaders(res, 'json');
      res.end(JSON.stringify(worlds));
    });
  },

};

