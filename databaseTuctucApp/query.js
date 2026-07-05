const { Client } = require('pg');

// ejemplo de uso: node query.js "SELECT * FROM profiles LIMIT 5;"

async function runQuery() {
  const query = process.argv.slice(2).join(' ');

  if (!query) {
    console.error("Por favor, proporciona una consulta SQL como argumento.");
    console.error("Ejemplo: node query.js \"SELECT * FROM trabajadores LIMIT 5;\"");
    process.exit(1);
  }

  const client = new Client({
    host: 'db.doypobovndxkbkikkozq.supabase.co',
    port: 5432,
    user: 'postgres',
    password: 'mariobross5625',
    database: 'postgres'
  });

  try {
    await client.connect();
    const res = await client.query(query);
    console.log(JSON.stringify(res.rows, null, 2));
  } catch (err) {
    console.error("Error al ejecutar la consulta:\n", err.message);
  } finally {
    await client.end();
  }
}

runQuery();
