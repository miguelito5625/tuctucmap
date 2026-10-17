import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-change-password-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <mat-icon class="title-icon">password</mat-icon>
      Cambiar Contraseña
    </h2>
    
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      <mat-dialog-content class="dialog-content">
        <p class="subtitle">Tu nueva contraseña debe tener al menos 6 caracteres.</p>
        
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nueva Contraseña</mat-label>
          <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="password">
          <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword" type="button" [attr.aria-label]="'Ocultar contraseña'">
            <mat-icon>{{hidePassword ? 'visibility_off' : 'visibility'}}</mat-icon>
          </button>
          <mat-error *ngIf="form.get('password')?.hasError('required')">Requerida</mat-error>
          <mat-error *ngIf="form.get('password')?.hasError('minlength')">Mínimo 6 caracteres</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width" style="margin-top: 8px;">
          <mat-label>Confirmar Contraseña</mat-label>
          <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="confirmPassword">
          <mat-error *ngIf="form.get('confirmPassword')?.hasError('required')">Requerida</mat-error>
          <mat-error *ngIf="form.hasError('mismatch')">Las contraseñas no coinciden</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      
      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button mat-dialog-close type="button">Cancelar</button>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || isSubmitting">
          {{ isSubmitting ? 'Guardando...' : 'Cambiar Contraseña' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .dialog-title {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #f8fafc;
      font-weight: 600;
      .title-icon { color: #fb923c; }
    }
    .subtitle { color: #94a3b8; margin-bottom: 20px; }
    .full-width { width: 100%; }
    .dialog-content {
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
      overflow-y: visible;
    }
    .dialog-actions {
      padding: 0 24px 24px;
      display: flex;
      gap: 8px;
    }
    @media (max-width: 480px) {
      .dialog-actions {
        padding: 0 16px 16px;
      }
    }
  `]
})
export class ChangePasswordDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly supabase = inject(SupabaseService);
  private readonly dialogRef = inject(MatDialogRef<ChangePasswordDialogComponent>);
  private readonly snackBar = inject(MatSnackBar);

  readonly form: FormGroup = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  isSubmitting = false;
  hidePassword = true;

  passwordMatchValidator(g: AbstractControl): ValidationErrors | null {
    const password = g.get('password')?.value;
    const confirmPassword = g.get('confirmPassword')?.value;
    return password === confirmPassword ? null : { mismatch: true };
  }

  async onSubmit() {
    if (this.form.invalid) return;
    this.isSubmitting = true;
    try {
      await this.supabase.updateAdminPassword(this.form.value.password);
      this.snackBar.open('Contraseña actualizada correctamente', 'Cerrar', {
        duration: 3000,
        panelClass: 'snack-success'
      });
      this.dialogRef.close(true);
    } catch (err: any) {
      this.snackBar.open('Error al actualizar contraseña: ' + (err.message || ''), 'Cerrar', {
        duration: 4000,
        panelClass: 'snack-error'
      });
    } finally {
      this.isSubmitting = false;
    }
  }
}
