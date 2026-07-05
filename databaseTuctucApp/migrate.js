const { Client } = require('pg');

const client = new Client({
  host: 'db.doypobovndxkbkikkozq.supabase.co',
  port: 5432,
  user: 'postgres',
  password: 'mariobross5625',
  database: 'postgres'
});

async function migrate() {
  try {
    await client.connect();
    
    console.log("Adding subscription_end_date column...");
    await client.query(`ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS subscription_end_date TIMESTAMPTZ;`);
    
    console.log("Creating driver_subscriptions table...");
    await client.query(`
        CREATE TABLE IF NOT EXISTS public.driver_subscriptions (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
            start_date TIMESTAMPTZ DEFAULT NOW(),
            end_date TIMESTAMPTZ NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        ALTER TABLE public.driver_subscriptions ENABLE ROW LEVEL SECURITY;
        DROP POLICY IF EXISTS "Subscriptions viewable by everyone" ON public.driver_subscriptions;
        CREATE POLICY "Subscriptions viewable by everyone" ON public.driver_subscriptions FOR SELECT USING (true);
        DROP POLICY IF EXISTS "Drivers can insert their own subscriptions" ON public.driver_subscriptions;
        CREATE POLICY "Drivers can insert their own subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (auth.uid() = driver_id);
    `);

    console.log("Migrating data...");
    // Only query profiles if trial_start_date still exists
    const columnsRes = await client.query(`
        SELECT column_name 
        FROM information_schema.columns 
        WHERE table_name='profiles' and column_name='trial_start_date';
    `);
    
    if (columnsRes.rows.length > 0) {
        const profilesRes = await client.query("SELECT * FROM public.profiles WHERE role = 'driver';");
        for (const row of profilesRes.rows) {
            if (row.trial_start_date && !row.subscription_end_date) {
                const trialStart = new Date(row.trial_start_date);
                const subEnd = new Date(trialStart.getTime() + (7 * 24 * 60 * 60 * 1000)); // + 7 days
                
                await client.query("UPDATE public.profiles SET subscription_end_date = $1 WHERE id = $2;", [subEnd.toISOString(), row.id]);
                await client.query("INSERT INTO public.driver_subscriptions (driver_id, start_date, end_date) VALUES ($1, $2, $3);", [row.id, trialStart.toISOString(), subEnd.toISOString()]);
                console.log(`Migrated driver: ${row.id}`);
            }
        }
        
        console.log("Dropping trial_start_date column...");
        await client.query(`ALTER TABLE public.profiles DROP COLUMN IF EXISTS trial_start_date;`);
    } else {
        console.log("trial_start_date column already dropped, skipping data migration.");
    }

    console.log("Migration complete!");
  } catch (err) {
    console.error("Migration failed:", err);
  } finally {
    await client.end();
  }
}

migrate();
