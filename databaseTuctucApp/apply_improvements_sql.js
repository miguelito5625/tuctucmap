const { Client } = require('pg');
const { dbConfig } = require('./db_config');

const client = new Client(dbConfig);

async function main() {
  await client.connect();
  console.log('Conectado a PostgreSQL en Supabase...');

  const queries = [
    'DROP POLICY IF EXISTS "Subscriptions viewable by everyone" ON public.driver_subscriptions;',
    'DROP POLICY IF EXISTS "Subscriptions viewable by owner and admin" ON public.driver_subscriptions;',
    `CREATE POLICY "Subscriptions viewable by owner and admin" ON public.driver_subscriptions 
     FOR SELECT USING (
       auth.uid() = driver_id OR 
       (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
     );`,
    `CREATE OR REPLACE FUNCTION public.delete_user_by_admin(target_user_id UUID)
     RETURNS VOID AS $$
     BEGIN
       IF (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin' THEN
         DELETE FROM auth.users WHERE id = target_user_id;
       ELSE
         RAISE EXCEPTION 'No autorizado: se requiere rol admin';
       END IF;
     END;
     $$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;`
  ];

  for (const q of queries) {
    console.log('Ejecutando:', q.trim().replace(/\s+/g, ' '));
    await client.query(q);
  }

  console.log('¡Políticas de seguridad RLS y funciones administrativas aplicadas exitosamente!');
  await client.end();
}

main().catch(err => {
  console.error('Error aplicando migración SQL:', err);
  process.exit(1);
});
