import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <div class="brand-logo">
            <mat-icon>electric_rickshaw</mat-icon>
          </div>
          <h1>Bienvenido de nuevo</h1>
          <p>Inicia sesión en el panel administrativo de TucTucMap</p>
        </div>

        <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
          <mat-form-field appearance="outline" class="form-field">
            <mat-label>Correo Electrónico</mat-label>
            <input matInput type="email" formControlName="email" placeholder="admin@tuctucmap.com" />
            <mat-icon matPrefix>email</mat-icon>
            @if (loginForm.controls['email'].hasError('required')) {
              <mat-error>El correo es obligatorio</mat-error>
            }
            @if (loginForm.controls['email'].hasError('email')) {
              <mat-error>Debe ser un correo válido</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="form-field">
            <mat-label>Contraseña</mat-label>
            <input
              matInput
              [type]="hidePassword() ? 'password' : 'text'"
              formControlName="password"
            />
            <mat-icon matPrefix>lock</mat-icon>
            <button
              mat-icon-button
              matSuffix
              type="button"
              (click)="hidePassword.set(!hidePassword())"
              [attr.aria-label]="'Hide password'"
              [attr.aria-pressed]="hidePassword()"
            >
              <mat-icon>{{ hidePassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            @if (loginForm.controls['password'].hasError('required')) {
              <mat-error>La contraseña es obligatoria</mat-error>
            }
          </mat-form-field>

          @if (errorMessage()) {
            <div class="error-message">
              <mat-icon>error_outline</mat-icon>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <button
            mat-flat-button
            color="primary"
            class="submit-btn"
            type="submit"
            [disabled]="loginForm.invalid || isLoading()"
          >
            @if (isLoading()) {
              <mat-spinner diameter="24" class="spinner"></mat-spinner>
            } @else {
              Entrar al Panel
            }
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: radial-gradient(circle at center, #1e293b 0%, #0f172a 100%);
      padding: 20px;
    }

    .login-card {
      background: rgba(30, 41, 59, 0.7);
      backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 24px;
      padding: 48px 40px;
      width: 100%;
      max-width: 420px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
    }

    .login-header {
      text-align: center;
      margin-bottom: 32px;

      .brand-logo {
        width: 64px;
        height: 64px;
        background: linear-gradient(135deg, #fb923c, #ea580c);
        border-radius: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin: 0 auto 20px;
        box-shadow: 0 10px 25px -5px rgba(234, 88, 12, 0.4);

        mat-icon {
          color: white;
          font-size: 36px;
          width: 36px;
          height: 36px;
        }
      }

      h1 {
        margin: 0 0 8px;
        font-size: 1.75rem;
        font-weight: 700;
        color: #f8fafc;
      }

      p {
        margin: 0;
        font-size: 0.95rem;
        color: #94a3b8;
      }
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .form-field {
      width: 100%;

      ::ng-deep {
        .mat-mdc-text-field-wrapper {
          background: rgba(15, 23, 42, 0.4) !important;
        }
        .mdc-notched-outline__leading,
        .mdc-notched-outline__notch,
        .mdc-notched-outline__trailing {
          border-color: rgba(255, 255, 255, 0.1) !important;
        }
        .mat-mdc-form-field-focus-overlay {
          background: transparent;
        }
        .mat-mdc-input-element {
          color: #f1f5f9 !important;
          caret-color: #fb923c;
        }
        .mat-mdc-floating-label {
          color: #94a3b8 !important;
        }
        .mat-icon {
          color: #64748b;
        }
        &.mat-focused {
          .mdc-notched-outline__leading,
          .mdc-notched-outline__notch,
          .mdc-notched-outline__trailing {
            border-color: #fb923c !important;
          }
          .mat-mdc-floating-label {
            color: #fb923c !important;
          }
          .mat-icon {
            color: #fb923c;
          }
        }
      }
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.2);
      border-radius: 12px;
      color: #ef4444;
      font-size: 0.85rem;
      margin-top: -8px;
      margin-bottom: 8px;

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    }

    .submit-btn {
      height: 52px;
      border-radius: 14px !important;
      font-size: 1.05rem !important;
      font-weight: 600 !important;
      background: linear-gradient(135deg, #fb923c, #ea580c) !important;
      color: white !important;
      margin-top: 8px;

      &:disabled {
        background: rgba(255, 255, 255, 0.1) !important;
        color: rgba(255, 255, 255, 0.3) !important;
      }

      &:not(:disabled):hover {
        filter: brightness(1.1);
      }

      .spinner {
        margin: 0 auto;
        ::ng-deep circle {
          stroke: white;
        }
      }
    }
  `]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly supabaseService = inject(SupabaseService);
  private readonly router = inject(Router);

  readonly hidePassword = signal(true);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.loginForm.value;

    try {
      const { error } = await this.supabaseService.signIn(email!, password!);
      
      if (error) {
        this.errorMessage.set('Credenciales incorrectas. Inténtalo de nuevo.');
      } else {
        await this.router.navigate(['/drivers']);
      }
    } catch (err) {
      this.errorMessage.set('Error de red. Verifica tu conexión.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
