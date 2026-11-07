const { Client } = require('pg');
const { dbConfig } = require('./db_config');

const PRESERVED_USER_IDS = [
  '11111111-1111-4111-8111-111111111111', // admin@tuctucmap.com (Administrador General)
  '22222222-2222-4222-8222-222222222221', // conductor1@gmail.com (Conductor 1)
  '22222222-2222-4222-8222-222222222222', // conductor2@gmail.com (Conductor 2)
  '33333333-3333-4333-8333-333333333331', // pasajero1@gmail.com (Pasajero 1)
  '33333333-3333-4333-8333-333333333332'  // pasajero2@gmail.com (Pasajero 2)
];

const PRESERVED_IDS_SQL = PRESERVED_USER_IDS.map(id => `'${id}'`).join(', ');

async function cleanDatabase(dryRun = false) {
  const client = new Client({
    ...dbConfig,
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log(`Conectado a la base de datos PostgreSQL. Modo: ${dryRun ? 'DRY-RUN (Simulación)' : 'EJECUCIÓN REAL'}`);

    await client.query('BEGIN');

    // 1. Eliminar viajes antiguos de prueba
    const tripsRes = await client.query('DELETE FROM public.trips;');
    console.log(`[trips] Registros de viajes eliminados: ${tripsRes.rowCount}`);

    // 2. Eliminar solicitudes de pago de suscripción de prueba
    const requestsRes = await client.query('DELETE FROM public.subscription_payment_requests;');
    console.log(`[subscription_payment_requests] Solicitudes de pago eliminadas: ${requestsRes.rowCount}`);

    // 3. Eliminar suscripciones de conductores no autorizados (conservando las de conductor1 y conductor2)
    const subsRes = await client.query(`DELETE FROM public.driver_subscriptions WHERE driver_id NOT IN (${PRESERVED_IDS_SQL});`);
    console.log(`[driver_subscriptions] Suscripciones de prueba eliminadas: ${subsRes.rowCount}`);

    // 4. Eliminar logs de crash de prueba
    const crashRes = await client.query('DELETE FROM public.app_crash_logs;');
    console.log(`[app_crash_logs] Logs de prueba eliminados: ${crashRes.rowCount}`);

    // 5. Eliminar perfiles de prueba en public.profiles
    const profilesRes = await client.query(`DELETE FROM public.profiles WHERE id NOT IN (${PRESERVED_IDS_SQL});`);
    console.log(`[public.profiles] Perfiles eliminados (manteniendo los 5 autorizados): ${profilesRes.rowCount}`);

    // 6. Eliminar usuarios en auth.users
    const authRes = await client.query(`DELETE FROM auth.users WHERE id NOT IN (${PRESERVED_IDS_SQL});`);
    console.log(`[auth.users] Usuarios eliminados de Supabase Auth: ${authRes.rowCount}`);

    // 7. Asegurar que Conductor 1 y Conductor 2 tengan vigencia activa y estado aprobado para las pruebas de Google
    const updateDriversRes = await client.query(`
      UPDATE public.profiles
      SET approval_status = 'aprobado',
          subscription_end_date = NOW() + INTERVAL '60 days'
      WHERE id IN ('22222222-2222-4222-8222-222222222221', '22222222-2222-4222-8222-222222222222');
    `);
    console.log(`[public.profiles] Conductores actualizados con estado activo para Google Review: ${updateDriversRes.rowCount}`);

    // 8. Verificar que app_config siga intacta
    const configRes = await client.query('SELECT count(*) FROM public.app_config;');
    console.log(`[app_config] Configuración comercial verificada e intacta: ${configRes.rows[0].count} registro(s)`);

    // 9. Verificar estado final de usuarios
    const remainingProfiles = await client.query('SELECT id, email, role, approval_status FROM public.profiles ORDER BY role, email;');
    console.log('\nPerfiles restantes en public.profiles:');
    console.table(remainingProfiles.rows);

    const remainingAuth = await client.query('SELECT id, email FROM auth.users ORDER BY email;');
    console.log('\nUsuarios restantes en auth.users:');
    console.table(remainingAuth.rows);

    if (dryRun) {
      await client.query('ROLLBACK');
      console.log('\n[DRY RUN] Transacción revertida exitosamente. Ningún cambio fue persistido.');
    } else {
      await client.query('COMMIT');
      console.log('\n[COMMIT] Limpieza confirmada y guardada exitosamente en la base de datos.');
    }

  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Error durante la limpieza (transacción revertida):', err);
    throw err;
  } finally {
    await client.end();
  }
}

const isDryRun = process.argv.includes('--dry-run');
cleanDatabase(isDryRun);
