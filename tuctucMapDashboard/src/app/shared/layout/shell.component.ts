import { Component, signal, inject, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';

import { SupabaseService } from '../../services/supabase.service';
import { ChangeNameDialogComponent } from '../dialogs/change-name-dialog.component';
import { ChangePasswordDialogComponent } from '../dialogs/change-password-dialog.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatTooltipModule,
    MatMenuModule,
    MatDialogModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  readonly sidenavOpened = signal(true);
  private readonly supabaseService = inject(SupabaseService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  adminName = signal<string>('Cargando...');

  ngOnInit() {
    this.loadProfile();
  }

  async loadProfile() {
    try {
      const profile = await this.supabaseService.getCurrentUserProfile();
      if (profile && profile.name) {
        this.adminName.set(profile.name);
      } else {
        this.adminName.set('Administrador');
      }
    } catch {
      this.adminName.set('Administrador');
    }
  }

  toggleSidenav(): void {
    this.sidenavOpened.update((v) => !v);
  }

  openChangeNameDialog() {
    const dialogRef = this.dialog.open(ChangeNameDialogComponent, {
      width: '400px',
      panelClass: 'dark-dialog'
    });

    dialogRef.afterClosed().subscribe((newName: string | undefined) => {
      if (newName) {
        this.adminName.set(newName);
      }
    });
  }

  openChangePasswordDialog() {
    this.dialog.open(ChangePasswordDialogComponent, {
      width: '400px',
      panelClass: 'dark-dialog'
    });
  }

  async logout(): Promise<void> {
    await this.supabaseService.signOut();
    this.router.navigate(['/login']);
  }
}
