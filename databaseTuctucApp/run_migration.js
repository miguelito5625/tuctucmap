const fs = require('fs');
const path = require('path');
const { Client } = require('pg');
const { dbConfig } = require('./db_config');

async function run() {
  const sqlFile = process.argv[2] || 'migration_renewal.sql';
  const filePath = path.resolve(__dirname, sqlFile);
  const sql = fs.readFileSync(filePath, 'utf8');

  const client = new Client(dbConfig);
  try {
    await client.connect();
    console.log(`Ejecutando archivo SQL: ${sqlFile}...`);
    await client.query(sql);
    console.log('Migración completada exitosamente.');
  } catch (err) {
    console.error('Error durante la migración:', err);
  } finally {
    await client.end();
  }
}

run();
