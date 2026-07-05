const { Client } = require('pg');

const client = new Client({
  host: 'db.doypobovndxkbkikkozq.supabase.co',
  port: 5432,
  user: 'postgres',
  password: 'mariobross5625',
  database: 'postgres'
});

async function run() {
  try {
    await client.connect();
    await client.query(`CREATE POLICY "Dashboard can update profiles" ON public.profiles FOR UPDATE USING (true);`);
    await client.query(`CREATE POLICY "Dashboard can insert subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (true);`);
    console.log("Policies added");
  } catch (err) {
    console.error("Error:\n", err.message);
  } finally {
    await client.end();
  }
}

run();
