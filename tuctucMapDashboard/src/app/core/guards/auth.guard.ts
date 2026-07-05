import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { SupabaseService } from '../../services/supabase.service';

export const authGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const supabaseService = inject(SupabaseService);

  const { data: { session } } = await supabaseService.session;

  if (session) {
    return true;
  }

  return router.parseUrl('/login');
};
