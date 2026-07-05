const { createClient } = require('@supabase/supabase-js');
const SUPABASE_URL = 'https://doypobovndxkbkikkozq.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRveXBvYm92bmR4a2JraWtrb3pxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMwMjMwMjQsImV4cCI6MjA5ODU5OTAyNH0.RCqCfUBCD1E_HXiWi9vw6OxwVlf7chddvVLCCTRewZU';
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

async function run() {
  const { data: authData, error: authError } = await supabase.auth.signInWithPassword({
    email: 'admin@tuctucmap.com',
    password: 'admin123'
  });
  if (authError) {
    console.error("Login error", authError);
    return;
  }
  
  // get a driver id
  const { data: drivers } = await supabase.from('profiles').select('*').eq('role', 'driver').limit(1);
  if (!drivers || drivers.length === 0) {
     console.log("No drivers found");
     return;
  }
  const driverId = drivers[0].id;
  console.log("Updating driver:", driverId);
  
  const { data: updateData, error: updateError } = await supabase
    .from('profiles')
    .update({ approval_status: 'rechazado' })
    .eq('id', driverId)
    .select();
    
  console.log("Update result:", { updateData, updateError });
}
run();
