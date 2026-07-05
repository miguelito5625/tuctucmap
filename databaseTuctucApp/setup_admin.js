const { Client } = require('pg');
const { createClient } = require('@supabase/supabase-js');

// Config from environment.ts
const SUPABASE_URL = 'https://doypobovndxkbkikkozq.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRveXBvYm92bmR4a2JraWtrb3pxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMwMjMwMjQsImV4cCI6MjA5ODU5OTAyNH0.RCqCfUBCD1E_HXiWi9vw6OxwVlf7chddvVLCCTRewZU';

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

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

    console.log("Updating database constraints and policies...");
    
    // 1. Alter constraint
    await client.query(`ALTER TABLE public.profiles DROP CONSTRAINT profiles_role_check;`);
    await client.query(`ALTER TABLE public.profiles ADD CONSTRAINT profiles_role_check CHECK (role IN ('passenger', 'driver', 'admin'));`);

    // 2. Drop old policies
    await client.query(`DROP POLICY IF EXISTS "Dashboard can update profiles" ON public.profiles;`);
    await client.query(`DROP POLICY IF EXISTS "Dashboard can insert subscriptions" ON public.driver_subscriptions;`);

    // 3. Add new policies
    await client.query(`DROP POLICY IF EXISTS "Admins can update profiles" ON public.profiles;`);
    await client.query(`
      CREATE POLICY "Admins can update profiles" ON public.profiles FOR UPDATE USING (
        (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
      );
    `);

    await client.query(`DROP POLICY IF EXISTS "Admins can insert subscriptions" ON public.driver_subscriptions;`);
    await client.query(`
      CREATE POLICY "Admins can insert subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (
        (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
      );
    `);

    console.log("Creating admin user...");
    
    // 4. Create user via Supabase Auth
    let { data: authData, error: authError } = await supabase.auth.signUp({
      email: 'admin@tuctucmap.com',
      password: 'admin123'
    });

    if (authError) {
      if (authError.message.includes('already registered')) {
        console.log("User already registered, logging in to get ID...");
        const loginRes = await supabase.auth.signInWithPassword({
            email: 'admin@tuctucmap.com',
            password: 'admin123'
        });
        if (loginRes.error) {
            console.error("Could not login to existing user:", loginRes.error.message);
        } else {
            authData = loginRes.data;
        }
      } else {
        throw authError;
      }
    }

    if (authData && authData.user) {
      const uid = authData.user.id;
      
      // Upsert profile in DB using pg to bypass RLS initially
      // (The user might not have a profile yet, or it might be created as 'passenger' if the app has a trigger, but there's no trigger in db.sql)
      await client.query(`
        INSERT INTO public.profiles (id, email, name, role)
        VALUES ($1, $2, 'Administrador', 'admin')
        ON CONFLICT (id) DO UPDATE SET role = 'admin';
      `, [uid, 'admin@tuctucmap.com']);

      console.log(`Admin user setup complete! ID: ${uid}`);
    }

  } catch (err) {
    console.error("Error:\n", err);
  } finally {
    await client.end();
  }
}

run();
