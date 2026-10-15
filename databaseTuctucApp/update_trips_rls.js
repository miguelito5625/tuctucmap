const { Client } = require('pg');

const client = new Client({
  host: 'db.doypobovndxkbkikkozq.supabase.co',
  port: 5432,
  user: 'postgres',
  password: 'mariobross5625',
  database: 'postgres'
});

async function main() {
  await client.connect();
  console.log('Connected to database...');

  const queries = [
    'DROP POLICY IF EXISTS "all_trips" ON public.trips;',
    'DROP POLICY IF EXISTS "Dashboard can view all trips" ON public.trips;',
    'DROP POLICY IF EXISTS "Trips viewable by participants" ON public.trips;',
    'DROP POLICY IF EXISTS "Passengers can request trips" ON public.trips;',
    'DROP POLICY IF EXISTS "allow_insert" ON public.trips;',
    'DROP POLICY IF EXISTS "Participants can update trips" ON public.trips;',
    'DROP POLICY IF EXISTS "Trips viewable by participants and admin" ON public.trips;',
    'DROP POLICY IF EXISTS "Trips insertable by participants" ON public.trips;',
    'DROP POLICY IF EXISTS "Participants and admin can update trips" ON public.trips;',

    `CREATE POLICY "Trips viewable by participants and admin" ON public.trips 
     FOR SELECT USING (
       auth.uid() = passenger_id OR 
       auth.uid() = driver_id OR 
       (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
     );`,

    `CREATE POLICY "Trips insertable by participants" ON public.trips 
     FOR INSERT WITH CHECK (
       auth.uid() = passenger_id OR 
       auth.uid() = driver_id
     );`,

    `CREATE POLICY "Participants and admin can update trips" ON public.trips 
     FOR UPDATE USING (
       auth.uid() = passenger_id OR 
       auth.uid() = driver_id OR 
       (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
     );`
  ];

  for (const q of queries) {
    console.log('Running:', q.trim().replace(/\s+/g, ' '));
    await client.query(q);
  }

  console.log('All trip policies updated successfully!');
  await client.end();
}

main().catch(err => {
  console.error('Error updating policies:', err);
  process.exit(1);
});
