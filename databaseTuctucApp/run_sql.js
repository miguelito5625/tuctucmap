const { Client } = require('pg');
const { dbConfig } = require('./db_config');

// Recibe la sentencia SQL por parámetro
const query = process.argv[2];

if (!query) {
  console.error("Por favor, proporciona una sentencia SQL como parámetro.");
  console.error("Ejemplo: node run_sql.js \"SELECT * FROM public.profiles;\"");
  process.exit(1);
}

const client = new Client(dbConfig);

async function runSQL() {
  try {
    await client.connect();
    console.log(`Ejecutando: ${query}`);
    
    const res = await client.query(query);
    
    if (res.command === 'SELECT') {
      console.table(res.rows);
      console.log(`\nTotal de filas: ${res.rowCount}`);
    } else {
      console.log(`Comando '${res.command}' ejecutado exitosamente.`);
      console.log(`Filas afectadas: ${res.rowCount}`);
    }
  } catch (err) {
    console.error("Error al ejecutar la sentencia:", err);
  } finally {
    await client.end();
  }
}

runSQL();
