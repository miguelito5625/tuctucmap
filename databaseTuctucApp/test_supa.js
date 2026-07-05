const { createClient } = require('@supabase/supabase-js');
const supabaseUrl = process.env.SUPABASE_URL || 'https://doypobovndxkbkikkozq.supabase.co'; // using the db url prefix
const supabaseKey = process.env.SUPABASE_ANON_KEY || 'REPLACE_ME'; 
