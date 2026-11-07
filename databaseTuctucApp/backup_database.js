const { Client } = require('pg');
const { dbConfig } = require('./db_config');
const fs = require('fs');
const path = require('path');

async function createBackup() {
  const client = new Client({
    ...dbConfig,
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log('Conectado a PostgreSQL en Supabase.');

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const backupDir = path.resolve(__dirname, 'backups');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }

    const tables = [
      { schema: 'auth', name: 'users' },
      { schema: 'public', name: 'profiles' },
      { schema: 'public', name: 'driver_subscriptions' },
      { schema: 'public', name: 'subscription_payment_requests' },
      { schema: 'public', name: 'trips' },
      { schema: 'public', name: 'app_config' },
      { schema: 'public', name: 'app_crash_logs' }
    ];

    const backupData = {};
    let sqlContent = `-- TucTuc Map Database Backup\n-- Generado: ${new Date().toISOString()}\n\n`;

    for (const t of tables) {
      console.log(`Extrayendo datos de ${t.schema}.${t.name}...`);
      const res = await client.query(`SELECT * FROM ${t.schema}.${t.name};`);
      backupData[`${t.schema}.${t.name}`] = res.rows;
      console.log(`-> ${res.rows.length} registros extraídos de ${t.schema}.${t.name}.`);

      if (res.rows.length > 0) {
        sqlContent += `-- Tabla: ${t.schema}.${t.name} (${res.rows.length} filas)\n`;
        for (const row of res.rows) {
          const cols = Object.keys(row);
          const vals = cols.map(c => {
            const val = row[c];
            if (val === null || val === undefined) return 'NULL';
            if (typeof val === 'number' || typeof val === 'boolean') return val;
            if (typeof val === 'object') return `'${JSON.stringify(val).replace(/'/g, "''")}'`;
            return `'${String(val).replace(/'/g, "''")}'`;
          });
          sqlContent += `INSERT INTO ${t.schema}.${t.name} (${cols.map(c => `"${c}"`).join(', ')}) VALUES (${vals.join(', ')}) ON CONFLICT DO NOTHING;\n`;
        }
        sqlContent += '\n';
      }
    }

    const jsonPath = path.join(backupDir, `backup_tuctuc_${timestamp}.json`);
    const sqlPath = path.join(backupDir, `backup_tuctuc_${timestamp}.sql`);

    fs.writeFileSync(jsonPath, JSON.stringify(backupData, null, 2), 'utf8');
    fs.writeFileSync(sqlPath, sqlContent, 'utf8');

    console.log(`\nRespaldo completado con éxito:`);
    console.log(`- JSON: ${jsonPath}`);
    console.log(`- SQL:  ${sqlPath}`);
    return { jsonPath, sqlPath, backupData };
  } catch (err) {
    console.error('Error durante el respaldo:', err);
    throw err;
  } finally {
    await client.end();
  }
}

if (require.main === module) {
  createBackup();
}

module.exports = { createBackup };
